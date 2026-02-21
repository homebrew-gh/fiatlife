package com.fiatlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credit_accounts")
data class CreditAccountEntity(
    @PrimaryKey
    val id: String,
    val jsonData: String,
    val type: String,
    val updatedAt: Long = System.currentTimeMillis()
)
