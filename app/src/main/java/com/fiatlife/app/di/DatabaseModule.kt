package com.fiatlife.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fiatlife.app.data.local.FiatLifeDatabase
import com.fiatlife.app.data.local.dao.BillDao
import com.fiatlife.app.data.local.dao.CreditAccountDao
import com.fiatlife.app.data.local.dao.CypherLogSubscriptionDao
import com.fiatlife.app.data.local.dao.GoalDao
import com.fiatlife.app.data.local.dao.SalaryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cypherlog_subscriptions (
                dTag TEXT NOT NULL PRIMARY KEY,
                eventId TEXT NOT NULL DEFAULT '',
                tagsJson TEXT NOT NULL DEFAULT '[]',
                createdAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS credit_accounts (
                id TEXT NOT NULL PRIMARY KEY,
                jsonData TEXT NOT NULL,
                type TEXT NOT NULL,
                updatedAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cypherlog_subscriptions ADD COLUMN contentDecryptedJson TEXT")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FiatLifeDatabase {
        return Room.databaseBuilder(
            context,
            FiatLifeDatabase::class.java,
            FiatLifeDatabase.DATABASE_NAME
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }

    @Provides
    fun provideSalaryDao(database: FiatLifeDatabase): SalaryDao = database.salaryDao()

    @Provides
    fun provideBillDao(database: FiatLifeDatabase): BillDao = database.billDao()

    @Provides
    fun provideGoalDao(database: FiatLifeDatabase): GoalDao = database.goalDao()

    @Provides
    fun provideCypherLogSubscriptionDao(database: FiatLifeDatabase): CypherLogSubscriptionDao =
        database.cypherLogSubscriptionDao()

    @Provides
    fun provideCreditAccountDao(database: FiatLifeDatabase): CreditAccountDao =
        database.creditAccountDao()
}
