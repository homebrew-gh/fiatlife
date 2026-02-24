package com.fiatlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "billers")
data class BillerEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val normalizedName: String,
    val linkedBillId: String?,
    val updatedAt: Long = System.currentTimeMillis()
)
