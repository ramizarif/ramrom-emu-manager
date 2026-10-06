package com.ramrom.emumanager.data

import android.content.Context

/**
 * DataModule: factory for database and DAOs.
 */
object DataModule {
    private var database: EmuDatabase? = null

    fun provideEmuDatabase(context: Context): EmuDatabase {
        return database ?: EmuDatabase.create(context).also {
            database = it
        }
    }

    fun provideAppStateDao(context: Context): AppStateDao {
        return provideEmuDatabase(context).appStateDao()
    }

    fun provideSetupPlanDao(context: Context): SetupPlanDao {
        return provideEmuDatabase(context).setupPlanDao()
    }

    fun provideWizardRunDao(context: Context): WizardRunDao {
        return provideEmuDatabase(context).wizardRunDao()
    }

    fun provideWizardStepDao(context: Context): WizardStepDao {
        return provideEmuDatabase(context).wizardStepDao()
    }

    fun provideLibraryGameDao(context: Context): LibraryGameDao {
        return provideEmuDatabase(context).libraryGameDao()
    }

    fun provideConfigChangeDao(context: Context): ConfigChangeDao {
        return provideEmuDatabase(context).configChangeDao()
    }

    fun provideImportJobDao(context: Context): ImportJobDao {
        return provideEmuDatabase(context).importJobDao()
    }

    fun provideImportItemDao(context: Context): ImportItemDao {
        return provideEmuDatabase(context).importItemDao()
    }

    fun provideHealthIssueDao(context: Context): HealthIssueDao {
        return provideEmuDatabase(context).healthIssueDao()
    }

    fun provideWishlistItemDao(context: Context): WishlistItemDao {
        return provideEmuDatabase(context).wishlistItemDao()
    }

    fun provideEntitlementDao(context: Context): EntitlementDao {
        return provideEmuDatabase(context).entitlementDao()
    }

    fun provideKnowledgeBundleStateDao(context: Context): KnowledgeBundleStateDao {
        return provideEmuDatabase(context).knowledgeBundleStateDao()
    }

    fun provideCustomizationDao(context: Context): CustomizationDao {
        return provideEmuDatabase(context).customizationDao()
    }
}
