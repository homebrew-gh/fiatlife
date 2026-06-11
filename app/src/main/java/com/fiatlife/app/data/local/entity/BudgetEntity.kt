package com.fiatlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budget_configs")
data class BudgetEntity(
    @PrimaryKey
    val id: String,
    val jsonData: String,
    val updatedAt: Long = System.currentTimeMillis()
)
