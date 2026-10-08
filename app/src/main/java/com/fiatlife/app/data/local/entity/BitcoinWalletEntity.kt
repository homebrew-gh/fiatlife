package com.fiatlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bitcoin_wallets")
data class BitcoinWalletEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    /** Full serialized `BitcoinWallet`. */
    val jsonData: String
)
