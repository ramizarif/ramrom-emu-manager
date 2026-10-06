package com.ramrom.emumanager.knowledge

import kotlinx.serialization.Serializable

// ============ Enums ============

enum class DeviceVerification {
    VERIFIED, COMMUNITY, UNVERIFIED
}

enum class SourceType {
    GITHUB_RELEASE, DIRECT_URL, PLAY_STORE, USER_OBTAINS, COMMUNITY_PORT
}

enum class SupportLevel {
    L1, L2, L3
}

enum class ConfigFormat {
    QT_INI, DOLPHIN_INI, XML, NONE
}

enum class VerifyKind {
    FOLDER_GRANT, FILE_EXISTS, PERMISSION, APP_INSTALLED, NONE
}

enum class ThemeVariableType {
    IMAGE, COLOR, FONT, ENUM
}

enum class BiosSource {
    ES_DE_DOCS, NO_INTRO, REDUMP
}

enum class GameSource {
    VERIFIED, COMMUNITY, HEURISTIC
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
)

@Serializable
data class DeviceProfile(
    val id: String,
    val names: List<String> = emptyList(),
    val match: DeviceMatchData = DeviceMatchData(),
    val chipFamily: String,
    val displays: List<Display> = emptyList(),
    val controls: Controls = Controls(),
    val verification: String = "unverified",
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
    val supportLevel: String = "L1",
    val paths: EmulatorPaths = EmulatorPaths(),
    val pathsNeedElevated: Boolean = false,
    val configFormat: String = "none",
    val enumMaps: Map<String, Map<String, String>> = emptyMap(),
    val permissions: List<String> = emptyList(),
    val declaresAllFilesAccess: Boolean = false,
    val folderPick: FolderPickSpec? = null,
    val inAppSetup: List<InAppSetupStep> = emptyList(),
    val firstLaunchCreatesDirs: Boolean = false,
    val esdeLaunch: EsdeLaunchConfig? = null,
    val romExtensions: List<String> = emptyList(),
    val companionExtensions: List<String> = emptyList(),
    val caveats: List<String> = emptyList()
)

@Serializable
data class InstallSource(
    val type: String = "user_obtains",
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
    val verifyTreeSuffix: String = ""
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
    val packages: List<String> = emptyList(),
    val source: FrontendSource = FrontendSource(),
    val paths: FrontendPaths = FrontendPaths(),
    val systemFolders: Map<String, String> = emptyMap(), // system id → folder name
    val mediaTypes: List<MediaType> = emptyList(),
    val mustBeClosedToWrite: Boolean = false,
    val knownCompanions: List<String> = emptyList()
)

@Serializable
data class FrontendSource(
    val type: String = "user_obtains",
    val note: String = ""
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
    val type: String = "", // image, color, font, enum
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
    val targets: List<BiosTarget> = emptyList(),
    val source: String = "" // es-de-docs, no-intro, redump
)

@Serializable
data class BiosTarget(
    val emulatorId: String = "",
    val path: String = "",
    val renameTo: String = ""
)

@Serializable
data class GameSettingsProfile(
    val gameKey: GameIdentity = GameIdentity(),
    val emulatorId: String = "",
    val emulatorVersionRange: String = "",
    val chipFamily: String = "",
    val name: String = "", // Recommended/Performance/Quality
    val settings: List<GameSetting> = emptyList(),
    val tradeoff: String = "",
    val verdict: String = "", // great, playable, poor, unknown
    val verifiedOn: List<String> = emptyList(),
    val source: String = "", // verified, community, heuristic
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
    val verify: String = "none"
)

@Serializable
data class HealthRuleParams(
    val ruleId: String = "",
    val params: Map<String, String> = emptyMap()
)
