package com.fiatlife.app.di

import android.content.Context
import androidx.room.Room
import com.fiatlife.app.data.local.FiatLifeDatabase
import com.fiatlife.app.data.local.dao.BillDao
import com.fiatlife.app.data.local.dao.GoalDao
import com.fiatlife.app.data.local.dao.SalaryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
        ).build()
    }

    @Provides
    fun provideSalaryDao(database: FiatLifeDatabase): SalaryDao = database.salaryDao()

    @Provides
    fun provideBillDao(database: FiatLifeDatabase): BillDao = database.billDao()

    @Provides
    fun provideGoalDao(database: FiatLifeDatabase): GoalDao = database.goalDao()
}
