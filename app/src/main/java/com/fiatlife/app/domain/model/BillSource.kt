package com.fiatlife.app.domain.model

/**
 * Source of a bill in the merged list: native (30078) or CypherLog (37004).
 */
enum class BillSource {
    /** Stored as FiatLife kind 30078 */
    NATIVE,
    /** From CypherLog kind 37004; may have [preservedTags] for round-trip when editing */
    CYPHERLOG
}

/**
 * A bill with its source and optional preserved tags (for CypherLog items).
 */
data class BillWithSource(
    val bill: Bill,
    val source: BillSource,
    /** For CYPHERLOG: tags to re-emit when publishing 37004 (company_id, linked_asset_*, etc.) */
    val preservedTags: Map<String, List<String>>? = null
) {
    val id: String get() = bill.id
    val isCypherLog: Boolean get() = source == BillSource.CYPHERLOG
}
