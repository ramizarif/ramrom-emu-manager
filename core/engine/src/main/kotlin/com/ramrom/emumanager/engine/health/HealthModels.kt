package com.ramrom.emumanager.engine.health

/**
 * Severity level for a health issue.
 */
enum class IssueSeverity {
    BLOCKER,   // Setup is incomplete; system is unplayable
    WARNING,   // Functionality limited but system may work
    INFO       // Just informational; no action needed
}

/**
 * Action the user can take to fix an issue.
 * UI maps each to a button (e.g., "Install", "Grant Folder Access", "Create Folder").
 */
sealed class FixAction {
    /**
     * Install an app from knowledge data.
     * @param appId emulator or frontend ID from the bundle
     */
    data class InstallApp(val appId: String) : FixAction()

    /**
     * Grant runtime permission to an app.
     * @param packageId the package to grant to
     * @param permission permission name (e.g., POST_NOTIFICATIONS)
     */
    data class GrantPermission(val packageId: String, val permission: String) : FixAction()

    /**
     * Grant all-files access to an app.
     * @param packageId the package to grant to
     */
    data class GrantAllFilesAccess(val packageId: String) : FixAction()

    /**
     * Guide user through folder grant in the app (SAF tree picker).
     * @param packageId the app to guide
     * @param systemId the system folder name (e.g., "switch")
     */
    data class GrantFolder(val packageId: String, val systemId: String) : FixAction()

    /**
     * Create a missing ROM folder.
     * @param folderPath absolute path like "/storage/emulated/0/ROMs/switch"
     */
    data class CreateFolder(val folderPath: String) : FixAction()

    /**
     * Stage BIOS files to an emulator (copy from user folder to emulator's BIOS dir).
     * @param systemId the system that needs BIOS
     */
    data class StageBios(val systemId: String) : FixAction()

    /**
     * Update or install ES-DE custom system entry.
     * @param systemId the system to configure in ES-DE
     */
    data class ConfigureInEsde(val systemId: String) : FixAction()

    /**
     * Open app to the setup screen.
     * @param appId app to open
     * @param setupStep which step to guide to
     */
    data class OpenAppSetup(val appId: String, val setupStep: String = "") : FixAction()

    /**
     * No action available; info only.
     */
    /** Turn Shizuku back on (F02). */
    object ReconnectAccess : FixAction()

    object None : FixAction()
}

/**
 * A single health issue found during checks.
 */
data class HealthIssue(
    val id: String,                    // unique ID like "emulator_missing_switch"
    val severity: IssueSeverity,
    val title: String,                 // short user-facing title, plain language
    val detail: String,                // explanation of what's wrong and why it matters
    val evidence: List<String> = emptyList(), // detailed evidence (paths, versions, etc.)
    val fix: FixAction = FixAction.None,
    /** The system the issue is about, for S14's per-system rows; null for app-wide ones. */
    val systemId: String? = null,
)
