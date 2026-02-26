package com.fiatlife.app.domain.model

import kotlinx.serialization.Serializable

/** Company/payee identity used to track recurring bills from the same biller. */
@Serializable
data class Biller(
    val id: String = "",
    val name: String = "",
    val normalizedName: String = "",
    /** Optional currently linked bill record for upsert-by-company flows. */
    val linkedBillId: String? = null,
    val isArchived: Boolean = false,
    val updatedAt: Long = 0L
)
