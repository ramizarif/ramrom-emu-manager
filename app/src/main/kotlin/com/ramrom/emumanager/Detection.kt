package com.ramrom.emumanager

import android.content.Context
import android.content.pm.PackageManager
import com.ramrom.emumanager.knowledge.DeviceDetector
import com.ramrom.emumanager.knowledge.KnowledgeStore
import com.ramrom.emumanager.knowledge.MatchKind
import com.ramrom.emumanager.screens.DeviceSummary
import com.ramrom.emumanager.screens.ExistingSetup
import com.ramrom.emumanager.ui.handheld.HandheldTile
import com.ramrom.emumanager.ui.theme.EmuColors

/** P01 + a read-only quick scan for S03. */
object Detection {
    private val tileColors = listOf(EmuColors.Berry, EmuColors.Sky, EmuColors.Tangerine, EmuColors.Grape, EmuColors.YourTurn, EmuColors.Sunshine, EmuColors.Mint)

    fun deviceSummary(context: Context, store: KnowledgeStore): DeviceSummary {
        val info = DeviceDetector.readDevice(context)
        val bundle = store.getBundle() ?: return DeviceSummary("This looks like a ${info.model}", info.socModel.orEmpty(), emptyList(), DeviceSummary.Kind.BestGuess)
        val m = DeviceDetector.match(info, bundle)
        val detail = listOfNotNull(info.socModel, m.chipFamily?.let { familyName(it.id) }).joinToString(" · ")
        return when {
            m.matchKind == MatchKind.MODEL -> DeviceSummary(
                "This looks like an ${m.profile!!.names.firstOrNull() ?: info.model}", detail,
                listOfNotNull(if (info.hasGamepad) "Controller detected" else null),
                if (m.isVerified) DeviceSummary.Kind.Verified else DeviceSummary.Kind.BestGuess,
            )
            m.isPhoneDetected -> DeviceSummary("This looks like a phone", detail, emptyList(), DeviceSummary.Kind.Phone)
            m.chipFamily != null -> DeviceSummary("This looks like a ${familyName(m.chipFamily!!.id)} handheld", "$detail · model “${info.model}” isn't in our list yet", emptyList(), DeviceSummary.Kind.BestGuess)
            else -> DeviceSummary("This looks like a ${info.model}", detail, emptyList(), DeviceSummary.Kind.BestGuess)
        }
    }

    /**
     * The model the user said this is (S02 "Choose another"), for hardware we can't recognise. Our
     * own detection wins when it knows the model; this fills in when it doesn't.
     */
    fun chosenDeviceId(context: Context): String? = context.getSharedPreferences("device", Context.MODE_PRIVATE).getString("chosen", null)
    fun setChosenDevice(context: Context, id: String?) { context.getSharedPreferences("device", Context.MODE_PRIVATE).edit().putString("chosen", id).apply() }

    /** The device profile to use: the detected model, else the one the user chose. */
    fun deviceProfile(context: Context, bundle: com.ramrom.emumanager.knowledge.KnowledgeBundle): com.ramrom.emumanager.knowledge.DeviceProfile? =
        DeviceDetector.match(DeviceDetector.readDevice(context), bundle).profile
            ?: chosenDeviceId(context)?.let { id -> bundle.devices.firstOrNull { it.id == id } }

    fun tileColor(i: Int) = tileColors[i % tileColors.size]

    fun familyName(id: String) = when (id) {
        "sd8gen2" -> "Snapdragon 8 Gen 2"; "sd8gen3" -> "Snapdragon 8 Gen 3"; "sd8elite" -> "Snapdragon 8 Elite"
        "g3xgen2" -> "Snapdragon G3x Gen 2"; "sd865" -> "Snapdragon 865"; else -> "Android"
    }

    /** Two-character dock labels, matching the design mockups. */
    fun shortLabel(id: String, name: String) = when (id) {
        "eden" -> "Ed"; "retroarch" -> "RA"; "duckstation" -> "Du"; "ppsspp" -> "PP"; "dolphin" -> "Do"; "azahar" -> "Az"
        "nethersx2" -> "N2"; "melonds" -> "mD"; "vita3k" -> "V3"; "cemu" -> "Ce"; "gamenative" -> "GN"; "winlator" -> "Wn"
        else -> name.take(2)
    }

    fun existingSetup(context: Context, store: KnowledgeStore): ExistingSetup? {
        val bundle = store.getBundle() ?: return null
        val pm = context.packageManager
        fun installed(pkg: String) = try { pm.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
        val frontends = bundle.frontends.filter { f -> f.packages.any(::installed) }
        val tiles = bundle.emulators.filter { e -> e.packages.any(::installed) }.mapIndexed { i, e ->
            HandheldTile(e.id, shortLabel(e.id, e.name), tileColors[i % tileColors.size])
        }
        val feTiles = frontends.map { HandheldTile(it.id, it.name.take(2), EmuColors.Ink) }
        return ExistingSetup(frontends.map { it.name.ifEmpty { it.id } }, feTiles + tiles)
    }
}
