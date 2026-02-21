package com.fiatlife.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local cache of a CypherLog subscription (kind 37004).
 * [tagsJson] is the full tag set for round-trip (preserve company_id, linked_asset_*, etc.).
 * [contentDecryptedJson] is the NIP-44 decrypted content when CypherLog uses encryption; used for display when tags are minimal.
 */
@Entity(tableName = "cypherlog_subscriptions")
data class CypherLogSubscriptionEntity(
    @PrimaryKey
    val dTag: String,
    val eventId: String = "",
    /** JSON array of tag pairs [[key, value], ...] to preserve order and unknown tags */
    val tagsJson: String = "[]",
    val createdAt: Long = 0L,
    /** When 37004 content was encrypted, decrypted JSON (same logical fields as tags) for display */
    val contentDecryptedJson: String? = null
)
