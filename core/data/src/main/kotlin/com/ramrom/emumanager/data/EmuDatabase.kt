package com.ramrom.emumanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * EmuDatabase: main Room database for RamROM Emu Manager.
 * Version 1 with schema export for migration tracking.
 */
@Database(
    entities = [
        AppState::class,
        SetupPlan::class,
        WizardRun::class,
        WizardStep::class,
        InventorySnapshot::class,
        LibraryGame::class,
        ConfigChange::class,
        ImportJob::class,
        ImportItem::class,
        HealthIssue::class,
        WishlistItem::class,
        Entitlement::class,
        KnowledgeBundleState::class,
        Customization::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class EmuDatabase : RoomDatabase() {
    abstract fun appStateDao(): AppStateDao
    abstract fun setupPlanDao(): SetupPlanDao
    abstract fun wizardRunDao(): WizardRunDao
    abstract fun wizardStepDao(): WizardStepDao
    abstract fun inventorySnapshotDao(): InventorySnapshotDao
    abstract fun libraryGameDao(): LibraryGameDao
    abstract fun configChangeDao(): ConfigChangeDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun importItemDao(): ImportItemDao
    abstract fun healthIssueDao(): HealthIssueDao
    abstract fun wishlistItemDao(): WishlistItemDao
    abstract fun entitlementDao(): EntitlementDao
    abstract fun knowledgeBundleStateDao(): KnowledgeBundleStateDao
    abstract fun customizationDao(): CustomizationDao

    companion object {
        private const val DB_NAME = "emumanager.db"

        fun create(context: Context): EmuDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                EmuDatabase::class.java,
                DB_NAME
            ).build()
        }
    }
}
