package com.fiatlife.app.data.notification

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fiatlife.app.data.local.dao.BillDao
import com.fiatlife.app.data.local.dao.CreditAccountDao
import com.fiatlife.app.domain.model.Bill
import com.fiatlife.app.domain.model.CreditAccount
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.time.LocalDate

private val Context.notifPrefsStore by preferencesDataStore(name = "bill_notif_prefs")

val KEY_NOTIF_ENABLED = booleanPreferencesKey("bill_notif_enabled")
val KEY_NOTIF_DETAIL_LEVEL = stringPreferencesKey("bill_notif_detail_level")
val KEY_NOTIF_DAYS_BEFORE = intPreferencesKey("bill_notif_days_before")
/** Comma-separated reminder days, e.g. "1,3,7". If missing, falls back to KEY_NOTIF_DAYS_BEFORE. */
val KEY_NOTIF_REMINDER_DAYS = stringPreferencesKey("bill_notif_reminder_days")

fun parseReminderDays(prefs: Preferences): Set<Int> {
    val csv = prefs[KEY_NOTIF_REMINDER_DAYS]
    if (!csv.isNullOrBlank()) {
        return csv.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..30 }.toSet()
    }
    val single = prefs[KEY_NOTIF_DAYS_BEFORE] ?: 3
    return setOf(single.coerceIn(1, 14))
}

enum class NotifDetailLevel { PRIVATE, DETAILED }

@HiltWorker
class BillReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val billDao: BillDao,
    private val creditAccountDao: CreditAccountDao,
    private val notificationManager: BillNotificationManager,
    private val json: Json
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.notifPrefsStore.data.first()
        val enabled = prefs[KEY_NOTIF_ENABLED] ?: false
        if (!enabled) return Result.success()

        val detailStr = prefs[KEY_NOTIF_DETAIL_LEVEL] ?: NotifDetailLevel.PRIVATE.name
        val detailed = detailStr == NotifDetailLevel.DETAILED.name
        val reminderDays = parseReminderDays(prefs)

        val bills = billDao.getAll().first()
        val creditAccounts = creditAccountDao.getAll().first()
        val accountsById = creditAccounts.mapNotNull { e ->
            try {
                json.decodeFromString<CreditAccount>(e.jsonData).let { acc -> acc.id to acc }
            } catch (_: Exception) { null }
        }.toMap()

        val now = System.currentTimeMillis()
        val todayStart = startOfTodayMillis()
        val today = LocalDate.now()

        for (entity in bills) {
            val bill = try {
                json.decodeFromString<Bill>(entity.jsonData)
            } catch (_: Exception) { continue }

            if (bill.isCancelled) continue
            if (bill.isPaidForCurrentCycle(now)) continue
            // Match UI: don't remind for credit/loan with $0 balance (nothing due to pay).
            if (bill.isCreditOrLoan()) {
                val balance = bill.linkedCreditAccountId?.let { accountsById[it]?.currentBalance }
                    ?: bill.creditCardDetails?.currentBalance ?: 0.0
                if (balance <= 0.0) continue
            }

            val nextDue = bill.nextDueDateMillis() ?: continue
            val daysUntil = ((nextDue - todayStart) / 86_400_000L).toInt()
            if (daysUntil in reminderDays) {
                notificationManager.showBillReminder(bill, daysUntil, detailed)
            }
        }

        // Debt/credit accounts not linked to a bill: payment due reminders
        for (entity in creditAccounts) {
            val account = try {
                json.decodeFromString<CreditAccount>(entity.jsonData)
            } catch (_: Exception) { continue }
            if (account.linkedBillId != null) continue
            if (account.effectiveMonthlyPayment() <= 0) continue
            val dueDay = account.dueDay.coerceIn(1, 28)
            val thisMonth = today.withDayOfMonth(dueDay)
            val nextDue = if (thisMonth.isBefore(today)) thisMonth.plusMonths(1) else thisMonth
            val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, nextDue).toInt()
            if (daysUntil in reminderDays) {
                notificationManager.showDebtReminder(
                    account.name,
                    account.effectiveMonthlyPayment(),
                    daysUntil,
                    account.id,
                    detailed
                )
            }
        }
        return Result.success()
    }

    /** Start of today (midnight) in ms, same reference as UI for "days until due". */
    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
