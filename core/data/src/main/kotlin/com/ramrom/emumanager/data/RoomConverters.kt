package com.ramrom.emumanager.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class RoomConverters {
    @TypeConverter
    fun appModeToString(mode: AppMode): String = mode.name

    @TypeConverter
    fun stringToAppMode(value: String): AppMode = AppMode.valueOf(value)

    @TypeConverter
    fun planOriginToString(origin: PlanOrigin): String = origin.name

    @TypeConverter
    fun stringToPlanOrigin(value: String): PlanOrigin = PlanOrigin.valueOf(value)

    @TypeConverter
    fun planSystemListToJson(systems: List<PlanSystem>): String =
        Json.encodeToString(systems)

    @TypeConverter
    fun jsonToPlanSystemList(value: String): List<PlanSystem> =
        if (value.isEmpty()) emptyList() else Json.decodeFromString(value)

    @TypeConverter
    fun wizardRunStatusToString(status: WizardRunStatus): String = status.name

    @TypeConverter
    fun stringToWizardRunStatus(value: String): WizardRunStatus = WizardRunStatus.valueOf(value)

    @TypeConverter
    fun wizardStepTypeToString(type: WizardStepType): String = type.name

    @TypeConverter
    fun stringToWizardStepType(value: String): WizardStepType = WizardStepType.valueOf(value)

    @TypeConverter
    fun wizardStepStatusToString(status: WizardStepStatus): String = status.name

    @TypeConverter
    fun stringToWizardStepStatus(value: String): WizardStepStatus = WizardStepStatus.valueOf(value)

    @TypeConverter
    fun wizardStepExecutorToString(executor: WizardStepExecutor): String = executor.name

    @TypeConverter
    fun stringToWizardStepExecutor(value: String): WizardStepExecutor = WizardStepExecutor.valueOf(value)

    @TypeConverter
    fun stringListToJson(list: List<String>): String = Json.encodeToString(list)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else Json.decodeFromString(value)

    @TypeConverter
    fun settingsStateToString(state: SettingsState): String = state.name

    @TypeConverter
    fun stringToSettingsState(value: String): SettingsState = SettingsState.valueOf(value)

    @TypeConverter
    fun configChangeTargetToString(target: ConfigChangeTarget): String = target.name

    @TypeConverter
    fun stringToConfigChangeTarget(value: String): ConfigChangeTarget = ConfigChangeTarget.valueOf(value)

    @TypeConverter
    fun configChangeReasonToString(reason: ConfigChangeReason): String = reason.name

    @TypeConverter
    fun stringToConfigChangeReason(value: String): ConfigChangeReason = ConfigChangeReason.valueOf(value)

    @TypeConverter
    fun importJobStatusToString(status: ImportJobStatus): String = status.name

    @TypeConverter
    fun stringToImportJobStatus(value: String): ImportJobStatus = ImportJobStatus.valueOf(value)

    @TypeConverter
    fun importItemActionToString(action: ImportItemAction): String = action.name

    @TypeConverter
    fun stringToImportItemAction(value: String): ImportItemAction = ImportItemAction.valueOf(value)

    @TypeConverter
    fun importItemStatusToString(status: ImportItemStatus): String = status.name

    @TypeConverter
    fun stringToImportItemStatus(value: String): ImportItemStatus = ImportItemStatus.valueOf(value)

    @TypeConverter
    fun healthIssueSeverityToString(severity: HealthIssueSeverity): String = severity.name

    @TypeConverter
    fun stringToHealthIssueSeverity(value: String): HealthIssueSeverity = HealthIssueSeverity.valueOf(value)

    @TypeConverter
    fun wishlistItemStatusToString(status: WishlistItemStatus): String = status.name

    @TypeConverter
    fun stringToWishlistItemStatus(value: String): WishlistItemStatus = WishlistItemStatus.valueOf(value)

    @TypeConverter
    fun entitlementStateToString(state: EntitlementState): String = state.name

    @TypeConverter
    fun stringToEntitlementState(value: String): EntitlementState = EntitlementState.valueOf(value)

    @TypeConverter
    fun customizationKindToString(kind: CustomizationKind): String = kind.name

    @TypeConverter
    fun stringToCustomizationKind(value: String): CustomizationKind = CustomizationKind.valueOf(value)
}
