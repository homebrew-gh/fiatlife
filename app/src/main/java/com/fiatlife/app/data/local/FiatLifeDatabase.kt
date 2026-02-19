package com.fiatlife.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fiatlife.app.data.local.dao.BillDao
import com.fiatlife.app.data.local.dao.GoalDao
import com.fiatlife.app.data.local.dao.SalaryDao
import com.fiatlife.app.data.local.entity.BillEntity
import com.fiatlife.app.data.local.entity.GoalEntity
import com.fiatlife.app.data.local.entity.SalaryEntity

@Database(
    entities = [
        SalaryEntity::class,
        BillEntity::class,
        GoalEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FiatLifeDatabase : RoomDatabase() {
    abstract fun salaryDao(): SalaryDao
    abstract fun billDao(): BillDao
    abstract fun goalDao(): GoalDao

    companion object {
        const val DATABASE_NAME = "fiatlife_db"
    }
}
