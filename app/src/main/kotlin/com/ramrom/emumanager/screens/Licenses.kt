package com.ramrom.emumanager.screens

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.layout.PageScaffold
import com.ramrom.emumanager.ui.theme.EmuType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One piece of open-source software in the app: what it is, its licence, and the bundled text. */
private data class Component(val name: String, val what: String, val license: String, val file: String)

private val COMPONENTS = listOf(
    Component("Jetpack Compose, Activity, Lifecycle, Room", "AndroidX, by Google", "Apache License 2.0", "apache-2.0.txt"),
    Component("Kotlin coroutines and serialization", "By JetBrains", "Apache License 2.0", "apache-2.0.txt"),
    Component("Shizuku API", "By RikkaApps", "Apache License 2.0", "apache-2.0.txt"),
    Component("7-Zip-JBinding-4Android", "Unpacks archives. By Boris Brodski and omicronapps", "GNU LGPL 2.1", "lgpl-2.1.txt"),
    Component("7-Zip", "By Igor Pavlov. Its RAR code may not be used to make RAR archives", "GNU LGPL 2.1 with unRAR restriction", "7-zip.txt"),
    Component("Bricolage Grotesque, Figtree, Geist Mono", "Fonts", "SIL Open Font License 1.1", "OFL-fonts.txt"),
)

/** S40 list. */
@Composable
fun LicensesScreen(onOpen: (file: String, title: String) -> Unit) {
    PageScaffold("Open-source licences", hints = listOf("A" to "Read", "B" to "Back")) {
        BasicText("RamROM Emu Manager is free software, and it's built with this software. Thanks to everyone who made it.", style = EmuType.Body)
        SectionLabel("This app", 1)
        ListRow("RamROM Emu Manager", detail = "GNU GPL 3.0 or later, with a 7-Zip exception", onClick = { onOpen("ramrom-gpl-3.0.txt", "GNU GPL 3.0") }) { BasicText("›", style = EmuType.Body) }
        SectionLabel("Inside this app", COMPONENTS.size)
        COMPONENTS.forEach { c -> ListRow(c.name, detail = "${c.what}  ·  ${c.license}", onClick = { onOpen(c.file, c.license) }) { BasicText("›", style = EmuType.Body) } }
    }
}

/** One licence's full text, from the app's own assets. */
@Composable
fun LicenseTextScreen(file: String, title: String) {
    val context = LocalContext.current
    val text by produceState("", file) {
        value = withContext(Dispatchers.IO) { runCatching { context.assets.open("licenses/$file").bufferedReader().readText() }.getOrDefault("") }
    }
    PageScaffold(title, hints = listOf("B" to "Back")) {
        BasicText(text, style = EmuType.Detail)
    }
}
