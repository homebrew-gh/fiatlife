package com.fiatlife.app.domain.model

/**
 * Merge local edits with a stored/remote copy so a stale empty config cannot
 * wipe paycheck logs that exist on another device.
 */
fun mergeSalaryConfigPreserveLogs(
    incoming: SalaryConfig,
    existing: SalaryConfig?
): SalaryConfig {
    if (existing == null) return incoming

    var merged = incoming
    if (incoming.paycheckLog.isEmpty() && existing.paycheckLog.isNotEmpty()) {
        merged = merged.copy(paycheckLog = existing.paycheckLog)
    }
    if (incoming.id.isEmpty() && existing.id.isNotEmpty()) {
        merged = merged.copy(id = existing.id)
    }
    if (incoming.payRateHistory.isEmpty() && existing.payRateHistory.isNotEmpty()) {
        merged = merged.copy(payRateHistory = existing.payRateHistory)
    }
    return merged
}
