package com.fiatlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey
    val id: String,
    val jsonData: String,
    val category: String,
    val updatedAt: Long = System.currentTimeMillis()
)
