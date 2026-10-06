package com.ramrom.emumanager.run

import com.ramrom.emumanager.engine.Step
import com.ramrom.emumanager.engine.StepType
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import com.ramrom.emumanager.knowledge.SourceType

/** What S13 should offer as its main button. */
enum class GuidedAction {
    /** Open the app the step is about. */
    OpenApp,
    /** Open the app's official download page. */
    OpenPage,
    /** Open Android's settings page for this app's permissions. */
    AppSettings,
    /** Open Android's "all files access" page for the app. */
    AllFilesSettings,
    /** Open that page for us, then do the step ourselves. */
    OwnAllFiles,
    /** We can do it now; Android asks the user to confirm (limited-mode installs). */
    RunNow,
    /** Nothing to open; the user does it their own way. */
    None,
}

/** Everything S12 and S13 say about one step. Plain words; the exact value is shown on its own. */
data class StepCopy(
    val title: String,
    val why: String,
    /** The one thing to pick or type exactly, e.g. `ROMs/switch`. */
    val value: String? = null,
    val valueLabel: String = "",
    val instructions: List<String> = emptyList(),
    val action: GuidedAction = GuidedAction.None,
    val actionLabel: String = "",
    /** The app [GuidedAction.OpenApp] etc. act on. */
    val appId: String? = null,
    val url: String? = null,
    /** True when we can check it ourselves; S13 then checks on return instead of offering "I did it". */
    val verifiable: Boolean,
)

object StepText {
    fun permissionName(p: String) = com.ramrom.emumanager.engine.PermissionNames.of(p)

    /** Frontend profiles carry no display name; ES-DE is the only one so far. */
    fun frontendName(fe: com.ramrom.emumanager.knowledge.FrontendProfile) = if (fe.id == "es-de") "ES-DE" else fe.id

    fun copy(step: Step, bundle: KnowledgeBundle): StepCopy {
        val emu = bundle.emulators.firstOrNull { it.id == step.subject }
        val fe = bundle.frontends.firstOrNull { it.id == step.subject }
        val name = emu?.name ?: fe?.let(::frontendName) ?: step.subject
        val frontend = bundle.frontends.firstOrNull()
        return when (step.type) {
            StepType.InstallApp -> StepCopy(
                "Install $name", "Android asks you to confirm each app it installs.",
                instructions = listOf("Select Install below", "Android shows its install prompt: tap Install", "Come back here; we carry on"),
                action = GuidedAction.RunNow, actionLabel = "Install $name", appId = step.subject, verifiable = true,
            )
            StepType.GuidedObtainApp -> {
                val page = emu?.source?.takeIf { it.type == SourceType.OFFICIAL_PAGE && it.url.isNotEmpty() }?.url
                StepCopy(
                    "Get $name", if (page != null) "$name is installed from its official site." else "$name isn't one we can install for you.",
                    instructions = if (page != null) listOf("Open the download page", "Download the Android version and install it", "Come back here; we'll spot it")
                    else listOf("Install $name the way you normally would", "Come back here; we'll spot it"),
                    action = if (page != null) GuidedAction.OpenPage else GuidedAction.None, actionLabel = "Open download page",
                    appId = step.subject, url = page, verifiable = true,
                )
            }
            StepType.FirstLaunch -> StepCopy(
                "Open $name once", "It makes its folders the first time it runs.",
                instructions = listOf("Open $name", "Wait for its main screen, then come back"),
                action = GuidedAction.OpenApp, actionLabel = "Open $name", appId = step.subject, verifiable = true,
            )
            StepType.GrantRuntimePermission -> StepCopy(
                "Allow $name's permissions", "$name asks for these to work properly.",
                value = emu?.permissions.orEmpty().joinToString(", ", transform = ::permissionName).ifEmpty { null }, valueLabel = "Allow",
                instructions = listOf("Open its app settings", "Tap Permissions and allow each one listed", "Come back here"),
                action = GuidedAction.AppSettings, actionLabel = "Open settings", appId = step.subject, verifiable = true,
            )
            StepType.GrantAllFilesAccess -> StepCopy(
                "Let $name manage files", "It reads games straight from your ROMs folder.",
                instructions = listOf("Open the setting", "Turn on \"Allow access to manage all files\"", "Come back here"),
                action = GuidedAction.AllFilesSettings, actionLabel = "Open setting", appId = step.subject, verifiable = true,
            )
            StepType.CreateFolder -> StepCopy(
                "Make the ${step.subject} folder", "${frontend?.let(::frontendName) ?: "Your frontend"} looks for games here.",
                value = "ROMs/${step.subject}", valueLabel = "Folder",
                instructions = listOf("Allow Emu Manager to manage files", "Come back here; we make the folders for you"),
                action = GuidedAction.OwnAllFiles, actionLabel = "Allow file access", verifiable = true,
            )
            StepType.WriteFrontendConfig -> StepCopy(
                "Point $name at your emulators", "So each system opens in the emulator you chose, not $name's default. $name is closed while we write.",
                action = GuidedAction.RunNow, actionLabel = "Set it up", appId = step.subject, verifiable = false,
            )
            StepType.WriteEmulatorConfig -> StepCopy(
                "Apply $name's settings for this device", "A few defaults that suit this handheld. Undo any time from Change history.",
                action = GuidedAction.RunNow, actionLabel = "Apply", appId = step.subject, verifiable = false,
            )
            StepType.StageBios -> {
                val system = bundle.systems.firstOrNull { it.id == step.subject }?.name ?: step.subject
                StepCopy(
                    "Copy your $system BIOS", "The emulator needs it to start games.",
                    instructions = listOf("Allow Emu Manager to manage files", "Come back here; we copy it for you"),
                    action = GuidedAction.OwnAllFiles, actionLabel = "Allow file access", verifiable = true,
                )
            }
            StepType.GuidedFolderPick -> {
                val folders = emu?.systems.orEmpty().mapNotNull { frontend?.systemFolders?.get(it) }.distinct()
                val steps = emu?.folderPick?.inAppSteps.orEmpty()
                StepCopy(
                    "Point $name at your games", "Android only lets you choose this inside $name.",
                    value = when (folders.size) { 0 -> "ROMs"; 1 -> "ROMs/${folders[0]}"; else -> "ROMs" }, valueLabel = "Pick this folder",
                    instructions = steps.ifEmpty { listOf("Open $name", "Choose the folder below when it asks") },
                    action = GuidedAction.OpenApp, actionLabel = "Open $name", appId = step.subject, verifiable = false,
                )
            }
            StepType.GuidedInAppSetup -> if (fe != null) StepCopy(
                "Finish $name's first run", "It asks a few questions the first time.",
                value = "ROMs", valueLabel = "When it asks for your ROM directory, pick",
                instructions = listOf("Open $name", "Follow its first-run screens", "Pick the folder below when it asks", "Come back here"),
                action = GuidedAction.OpenApp, actionLabel = "Open $name", appId = step.subject, verifiable = false,
            ) else StepCopy(
                emu?.inAppSetup?.singleOrNull()?.title?.let { "$it for $name" } ?: "Finish setting up $name",
                "This part is yours to do inside $name.",
                instructions = emu?.inAppSetup.orEmpty().map { it.instruction.ifEmpty { it.title } },
                action = GuidedAction.OpenApp, actionLabel = "Open $name", appId = step.subject, verifiable = false,
            )
            StepType.Import -> StepCopy("Import your games", "", verifiable = false)
            StepType.HealthCheck -> StepCopy("Check everything works", "", verifiable = true)
            else -> StepCopy(step.subject, "", verifiable = false)
        }
    }
}
