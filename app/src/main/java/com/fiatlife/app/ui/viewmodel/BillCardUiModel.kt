package com.fiatlife.app.ui.viewmodel

import com.fiatlife.app.domain.model.BillWithSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BillCardUiModel(
    val item: BillWithSource,
    val linkedAccountName: String? = null,
    val linkedAccountId: String? = null,
    val linkedAccountBalance: Double? = null,
    val isPaidForCycle: Boolean = false,
    val showPayButton: Boolean = false,
    val dueDateText: String? = null,
    val countdownLabel: String? = null,
    val showPastDue: Boolean = false,
    val amountDue: Double = 0.0
)

private val billCardDateFormat = ThreadLocal.withInitial {
    SimpleDateFormat("MMM d", Locale.getDefault())
}

internal fun buildBillCardUiModel(
    item: BillWithSource,
    creditAccountsById: Map<String, com.fiatlife.app.domain.model.CreditAccount>,
    now: Long = System.currentTimeMillis()
): BillCardUiModel {
    val bill = item.bill
    val linkedId = bill.linkedCreditAccountId
    val linkedAccount = linkedId?.let { creditAccountsById[it] }
    val linkedAccountName = linkedAccount?.name
    val linkedAccountBalance = linkedAccount?.currentBalance
    val isPaidForCycle = bill.isPaidForCurrentCycle(now)
    val effectiveBalance = linkedAccountBalance ?: bill.creditCardDetails?.currentBalance ?: 0.0
    val showPayButton = if (bill.isCreditOrLoan()) effectiveBalance > 0.0 else !isPaidForCycle
    val dueMillis = if (bill.isCreditOrLoan()) {
        bill.nextDueDateMillis()
    } else {
        if (bill.isPastDue() && !isPaidForCycle) bill.lastDueDateMillis() else bill.nextDueDateMillis()
    }
    val showPastDue = bill.isPastDue() &&
        !isPaidForCycle &&
        (!bill.isCreditOrLoan() || (dueMillis != null && dueMillis <= now))
    val overdueReferenceMillis = bill.lastDueDateMillis()
    val dueDateText = dueMillis?.let { billCardDateFormat.get().format(Date(it)) }
    val daysUntilDue = dueMillis?.let { millis -> daysBetweenStartOfDays(now, millis) }
    val countdownLabel = when {
        isPaidForCycle -> "Paid"
        dueMillis == null -> null
        showPastDue -> {
            val overdueFrom = overdueReferenceMillis ?: dueMillis
            val daysOverdue = (((now - overdueFrom) / 86_400_000L).toInt() + 1).coerceAtLeast(1)
            "$daysOverdue d overdue"
        }
        daysUntilDue == 0 -> "Due today"
        daysUntilDue == 1 -> "Due tomorrow"
        else -> "$daysUntilDue d left"
    }
    return BillCardUiModel(
        item = item,
        linkedAccountName = linkedAccountName,
        linkedAccountId = linkedId,
        linkedAccountBalance = linkedAccountBalance,
        isPaidForCycle = isPaidForCycle,
        showPayButton = showPayButton,
        dueDateText = dueDateText,
        countdownLabel = countdownLabel,
        showPastDue = showPastDue,
        amountDue = bill.effectiveAmountDue()
    )
}

private fun daysBetweenStartOfDays(now: Long, dueMillis: Long): Int {
    val nowCal = startOfDayCalendar(now)
    val dueCal = startOfDayCalendar(dueMillis)
    return ((dueCal.timeInMillis - nowCal.timeInMillis) / 86_400_000L).toInt()
}

private fun startOfDayCalendar(millis: Long): Calendar =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
