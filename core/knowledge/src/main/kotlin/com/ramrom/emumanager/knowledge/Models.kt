package com.ramrom.emumanager.knowledge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============ Enums ============

@Serializable
enum class DeviceVerification {
    @SerialName("verified")
    VERIFIED,
    @SerialName("community")
    COMMUNITY,
    @SerialName("unverified")
    UNVERIFIED
}

@Serializable
enum class SourceType {
    @SerialName("github_release")
    GITHUB_RELEASE,
    @SerialName("direct_url")
    DIRECT_URL,
    /** A Forgejo/Gitea host (same release API shape as GitHub); `url` is the host, `repo` owner/name. */
    @SerialName("forgejo_release")
    FORGEJO_RELEASE,
    /** The official download is a web page, not a stable APK link: the user downloads it there. */
    @SerialName("official_page")
    OFFICIAL_PAGE,
    @SerialName("play_store")
    PLAY_STORE,
    @SerialName("user_obtains")
    USER_OBTAINS,
    @SerialName("community_port")
    COMMUNITY_PORT
}

@Serializable
enum class SupportLevel {
    @SerialName("L1")
    L1,
    @SerialName("L2")
    L2,
    @SerialName("L3")
    L3
}

@Serializable
enum class ConfigFormat {
    @SerialName("qt_ini")
    QT_INI,
    @SerialName("dolphin_ini")
    DOLPHIN_INI,
    @SerialName("xml")
    XML,
    @SerialName("none")
    NONE
}

@Serializable
enum class VerifyKind {
    @SerialName("folderGrant")
    FOLDER_GRANT,
    @SerialName("fileExists")
    FILE_EXISTS,
    @SerialName("permission")
    PERMISSION,
    @SerialName("appInstalled")
    APP_INSTALLED,
    @SerialName("none")
    NONE
}

@Serializable
enum class ThemeVariableType {
    @SerialName("image")
    IMAGE,
    @SerialName("color")
    COLOR,
    @SerialName("font")
    FONT,
    @SerialName("enum")
    ENUM
}

@Serializable
enum class BiosSource {
    @SerialName("es-de-docs")
    ES_DE_DOCS,
    @SerialName("no-intro")
    NO_INTRO,
    @SerialName("redump")
    REDUMP
}

@Serializable
enum class GameSource {
    @SerialName("verified")
    VERIFIED,
    @SerialName("community")
    COMMUNITY,
    @SerialName("heuristic")
    HEURISTIC
}

@Serializable
enum class MatchKind {
    @SerialName("model")
    MODEL,
    @SerialName("family")
    FAMILY,
    @SerialName("none")
    NONE
}

// ============ Knowledge Data Classes ============

@Serializable
data class KnowledgeBundle(
    val schemaVersion: Int,
    val dataVersion: String,
    val devices: List<DeviceProfile> = emptyList(),
    val chipFamilies: List<ChipFamily> = emptyList(),
    val emulators: List<EmulatorProfile> = emptyList(),
    val frontends: List<FrontendProfile> = emptyList(),
    val themes: List<ThemeCapability> = emptyList(),
    val systems: List<SystemDef> = emptyList(),
    val bios: List<BiosEntry> = emptyList(),
    val settingsProfiles: List<GameSettingsProfile> = emptyList(),
    val guidedSteps: List<GuidedStepContent> = emptyList(),
    val healthRules: List<HealthRuleParams> = emptyList()
) {
    /** ES-DE, whose folder names every supported frontend shares, and whose config RamROM writes. */
    val esde: FrontendProfile? get() = frontends.firstOrNull { it.id == ESDE }

    companion object { const val ESDE = "es-de" }
}

@Serializable
data class DeviceProfile(
    val id: String,
    val names: List<String> = emptyList(),
    val match: DeviceMatchData = DeviceMatchData(),
    val chipFamily: String,
    val displays: List<Display> = emptyList(),
    val controls: Controls = Controls(),
    val verification: DeviceVerification = DeviceVerification.UNVERIFIED,
    val notes: String = ""
)

@Serializable
data class DeviceMatchData(
    val models: List<String> = emptyList(),
    val devices: List<String> = emptyList(),
    val socModels: List<String> = emptyList()
)

@Serializable
data class Display(
    val role: String, // "main" or "secondary"
    val widthPx: Int,
    val heightPx: Int,
    val dpi: Int
)

@Serializable
data class Controls(
    val gamepad: Boolean = false,
    val extraButtons: List<String> = emptyList()
)

@Serializable
data class ChipFamily(
    val id: String,
    val socModels: List<String> = emptyList(),
    val gpu: String = "",
    val tier: String = ""
)

@Serializable
data class EmulatorProfile(
    val id: String,
    val name: String = "",
    val systems: List<String> = emptyList(),
    val packages: List<String> = emptyList(),
    val source: InstallSource = InstallSource(),
    val minAndroid: Int = 0,
    val supportLevel: SupportLevel = SupportLevel.L1,
    val paths: EmulatorPaths = EmulatorPaths(),
    val pathsNeedElevated: Boolean = false,
    val configFormat: ConfigFormat = ConfigFormat.NONE,
    val enumMaps: Map<String, Map<String, String>> = emptyMap(),
    val permissions: List<String> = emptyList(),
    val declaresAllFilesAccess: Boolean = false,
    val folderPick: FolderPickSpec? = null,
    val inAppSetup: List<InAppSetupStep> = emptyList(),
    val firstLaunchCreatesDirs: Boolean = false,
    val esdeLaunch: EsdeLaunchConfig? = null,
    /** How games reach ES-DE when they don't live as files in ROMs (Vita3K installs them itself). */
    val romFolderNote: String = "",
    val romExtensions: List<String> = emptyList(),
    val companionExtensions: List<String> = emptyList(),
    val caveats: List<String> = emptyList(),
    /**
     * Eden-style INI: a setting only sticks with `key\default=false` beside it, and a per-game
     * file also needs `key\use_global=false`.
     */
    val marksNonDefault: Boolean = false,
)

@Serializable
data class InstallSource(
    val type: SourceType = SourceType.USER_OBTAINS,
    val repo: String = "",
    val assetRegex: String = "",
    val url: String = "",
    val note: String = ""
)

@Serializable
data class EmulatorPaths(
    val configFile: String = "",
    val perGameDir: String = "",
    val perGameFileName: String = "",
    val biosDir: String = "",
    val keysDir: String = "",
    val logFile: String = ""
)

@Serializable
data class FolderPickSpec(
    val needed: Boolean = true,
    val inAppSteps: List<String> = emptyList(),
    val verifyTreeSuffix: String = "",
    val verify: VerifyKind = VerifyKind.NONE
)

@Serializable
data class InAppSetupStep(
    val title: String = "",
    val instruction: String = ""
)

@Serializable
data class EsdeLaunchConfig(
    val findRuleName: String = "",
    val packageActivity: String = "",
    val command: String = ""
)

@Serializable
data class FrontendProfile(
    val id: String,
    val name: String = "",
    val packages: List<String> = emptyList(),
    val source: InstallSource = InstallSource(),
    /**
     * It reads games from a folder the user picks through Android's picker, so the grant shows which
     * folder it's pointed at (Cocoon, RetroHrai!). ES-DE does too; iiSU so far doesn't say.
     */
    val gamesFolderGrant: Boolean = false,
    /** Where the app keeps its games folder after first run, as its menus name it; empty if unknown. */
    val folderSetting: String = "",
    /** Reads the games through "All files access" (Cocoon, iiSU): without it the library is empty. */
    val declaresAllFilesAccess: Boolean = false,
    /**
     * Each system's emulator is chosen inside the app, in storage we can't reach: the user does it,
     * guided. Empty when RamROM writes it (ES-DE's gamelists).
     */
    val emulatorChoiceInApp: String = "",
    /** What to do on first open (pick the games folder), for the guided step. */
    val firstRunSteps: List<String> = emptyList(),
    val paths: FrontendPaths = FrontendPaths(),
    val systemFolders: Map<String, String> = emptyMap(), // system id → folder name
    /**
     * Other folder names a system's games are found in (a custom ES-DE system like `ps1`). Scanned
     * and recognised, never created: new games go to [systemFolders].
     */
    val folderAliases: Map<String, List<String>> = emptyMap(),
    /**
     * ES-DE's labels, from its bundled es_systems.xml (Android): the command each system launches by
     * default, the label that picks a standalone emulator, and per-package overrides (a nightly
     * build). RetroArch has none: ES-DE's defaults already are RetroArch cores.
     */
    val defaultLabels: Map<String, String> = emptyMap(),
    val emulatorLabels: Map<String, String> = emptyMap(),
    val packageLabels: Map<String, String> = emptyMap(),
    val mediaTypes: List<MediaType> = emptyList(),
    val mustBeClosedToWrite: Boolean = false,
    val knownCompanions: List<String> = emptyList()
)

@Serializable
data class FrontendPaths(
    val settings: String = "",
    val customSystems: String = "",
    val mediaRoot: String = "",
    val gamelists: String = "",
    val scripts: String = "",
    val themes: String = ""
)

@Serializable
data class MediaType(
    val folder: String = "",
    val extensions: List<String> = emptyList(),
    val aspect: String = ""
)

@Serializable
data class ThemeCapability(
    val themeId: String,
    val customization: ThemeCustomization = ThemeCustomization(),
    val variables: List<ThemeVariable> = emptyList(),
    val imageTargets: Map<String, String> = emptyMap()
)

@Serializable
data class ThemeCustomization(
    val supported: Boolean = false,
    val folder: String = "",
    val file: String = "",
    val template: String = "",
    val settingKey: String = "",
    val settingValue: String = "",
    val survivesUpdates: Boolean = false
)

@Serializable
data class ThemeVariable(
    val name: String = "",
    val type: ThemeVariableType = ThemeVariableType.COLOR,
    val default: String = ""
)

@Serializable
data class SystemDef(
    val id: String,
    val name: String = "",
    val maker: String = "",
    val biosRequired: Boolean = false,
    val recommended: Map<String, String> = emptyMap(), // chipFamily → emulatorId
    val alternatives: List<String> = emptyList()
)

@Serializable
data class BiosEntry(
    val system: String = "",
    val fileName: String = "",
    val md5: String = "",
    val sha1: String = "",
    val size: Long = 0,
    val variant: String = "",
    val required: Boolean = false,
    /** Any console dump of [size] works (PS2), not only this one; the emulator renames what it imports. */
    val anyDump: Boolean = false,
    val targets: List<BiosTarget> = emptyList(),
    val source: BiosSource? = null
)

@Serializable
data class BiosTarget(
    val emulatorId: String = "",
    val path: String = "",
    val renameTo: String = ""
)

@Serializable
data class GameSettingsProfile(
    /** No title ID: the emulator's defaults for this chip family (L2), written to its main config. */
    val gameKey: GameIdentity = GameIdentity(),
    val emulatorId: String = "",
    val emulatorVersionRange: String = "",
    val chipFamily: String = "",
    /** Only on this device (a dual-screen layout); empty means any device of the chip family. */
    val device: String = "",
    val name: String = "",
    val settings: List<GameSetting> = emptyList(),
    val tradeoff: String = "",
    val verdict: String = "",
    val verifiedOn: List<String> = emptyList(),
    val source: GameSource? = null,
    val notes: String = "",
    val updated: String = ""
)

@Serializable
data class GameIdentity(
    val system: String = "",
    val titleId: String = "",
    val serial: String = "",
    val hashes: List<String> = emptyList(),
    val name: String = ""
)

@Serializable
data class GameSetting(
    val key: String = "",
    val value: String = "",
    val label: String = ""
)

@Serializable
data class TitleIndexEntry(
    val system: String = "",
    val name: String = "",
    val region: String = "",
    val year: Int = 0,
    val ids: Map<String, String> = emptyMap(),
    val hashes: List<String> = emptyList()
)

@Serializable
data class GuidedStepContent(
    val id: String,
    val title: String = "",
    val why: String = "",
    val value: String = "",
    val steps: List<String> = emptyList(),
    val screenshot: String = "",
    val verify: VerifyKind = VerifyKind.NONE
)

@Serializable
data class HealthRuleParams(
    val ruleId: String = "",
    val params: Map<String, String> = emptyMap()
)
