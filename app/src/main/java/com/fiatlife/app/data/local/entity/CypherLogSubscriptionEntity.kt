package com.fiatlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of a CypherLog subscription (kind 37004).
 * [tagsJson] is the full tag set for round-trip (preserve company_id, linked_asset_*, etc.).
 */
@Entity(tableName = "cypherlog_subscriptions")
data class CypherLogSubscriptionEntity(
    @PrimaryKey
    val dTag: String,
    val eventId: String = "",
    /** JSON array of tag pairs [[key, value], ...] to preserve order and unknown tags */
    val tagsJson: String = "[]",
    val createdAt: Long = 0L
)
