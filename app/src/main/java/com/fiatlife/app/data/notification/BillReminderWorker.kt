package com.fiatlife.app.data.notification

import android.content.Context
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
import com.fiatlife.app.domain.model.BillFrequency
import com.fiatlife.app.domain.model.CreditAccount
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val Context.notifPrefsStore by preferencesDataStore(name = "bill_notif_prefs")

val KEY_NOTIF_ENABLED = booleanPreferencesKey("bill_notif_enabled")
val KEY_NOTIF_DETAIL_LEVEL = stringPreferencesKey("bill_notif_detail_level")
val KEY_NOTIF_DAYS_BEFORE = intPreferencesKey("bill_notif_days_before")

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
        val daysBefore = prefs[KEY_NOTIF_DAYS_BEFORE] ?: 3

        val bills = billDao.getAll().first()
        val today = LocalDate.now()

        for (entity in bills) {
            val bill = try {
                json.decodeFromString<Bill>(entity.jsonData)
            } catch (_: Exception) { continue }

            val nextDueDates = computeNextDueDates(bill, today)
            for (dueDate in nextDueDates) {
                val daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate).toInt()
                if (daysUntil in 0..daysBefore) {
                    notificationManager.showBillReminder(bill, daysUntil, detailed)
                }
            }
        }

        // Debt/credit accounts not linked to a bill: payment due reminders
        val creditAccounts = creditAccountDao.getAll().first()
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
            if (daysUntil in 0..daysBefore) {
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

    private fun computeNextDueDates(bill: Bill, today: LocalDate): List<LocalDate> {
        val dueDay = bill.dueDay.coerceIn(1, 28)
        return when (bill.frequency) {
            BillFrequency.MONTHLY -> {
                val thisMonth = today.withDayOfMonth(dueDay)
                if (thisMonth.isBefore(today)) listOf(thisMonth.plusMonths(1)) else listOf(thisMonth)
            }
            BillFrequency.WEEKLY -> {
                (0L..6L).map { today.plusDays(it) }.filter { it.dayOfWeek.value == dueDay.coerceIn(1, 7) }
            }
            BillFrequency.BIWEEKLY -> {
                val thisMonth = today.withDayOfMonth(dueDay)
                val dates = mutableListOf<LocalDate>()
                if (!thisMonth.isBefore(today)) dates.add(thisMonth)
                val plus14 = thisMonth.plusDays(14)
                if (!plus14.isBefore(today)) dates.add(plus14)
                if (dates.isEmpty()) dates.add(thisMonth.plusMonths(1))
                dates
            }
            BillFrequency.BIMONTHLY -> {
                val thisMonth = today.withDayOfMonth(dueDay)
                if (thisMonth.isBefore(today)) listOf(thisMonth.plusMonths(2)) else listOf(thisMonth)
            }
            BillFrequency.QUARTERLY -> {
                val thisMonth = today.withDayOfMonth(dueDay)
                if (thisMonth.isBefore(today)) listOf(thisMonth.plusMonths(3)) else listOf(thisMonth)
            }
            BillFrequency.SEMIANNUALLY -> {
                val thisMonth = today.withDayOfMonth(dueDay)
                if (thisMonth.isBefore(today)) listOf(thisMonth.plusMonths(6)) else listOf(thisMonth)
            }
            BillFrequency.ANNUALLY -> {
                val thisYear = today.withDayOfMonth(dueDay).withMonth(today.monthValue)
                if (thisYear.isBefore(today)) listOf(thisYear.plusYears(1)) else listOf(thisYear)
            }
        }
    }
}
