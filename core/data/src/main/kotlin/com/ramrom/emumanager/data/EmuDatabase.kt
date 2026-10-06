package com.ramrom.emumanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * EmuDatabase: main Room database for RamROM Emu Manager.
 * Schema exported for migration tracking; every version bump ships a migration.
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
    version = 2,
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

        /** P09 history: where each config change went and how to undo it. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE config_changes ADD COLUMN filePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE config_changes ADD COLUMN pkg TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE config_changes ADD COLUMN title TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE config_changes ADD COLUMN existedBefore INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE config_changes ADD COLUMN writtenPath TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): EmuDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                EmuDatabase::class.java,
                DB_NAME
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
}
