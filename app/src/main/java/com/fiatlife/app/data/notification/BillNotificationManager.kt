package com.fiatlife.app.data.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fiatlife.app.MainActivity
import com.fiatlife.app.R
import com.fiatlife.app.domain.model.Bill
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillNotificationManager @Inject constructor(
    private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "bill_reminders"
        private const val CHANNEL_NAME = "Bill Reminders"
        private const val CHANNEL_DESC = "Notifications for upcoming bill due dates"
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESC
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    @SuppressLint("MissingPermission")
    fun showBillReminder(bill: Bill, daysUntilDue: Int, detailed: Boolean) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, bill.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, body) = if (detailed) {
            val dueText = when (daysUntilDue) {
                0 -> "due today"
                1 -> "due tomorrow"
                else -> "due in $daysUntilDue days"
            }
            val amountStr = "$%.2f".format(bill.effectiveAmountDue())
            "${bill.name} — $amountStr" to "${bill.effectiveSubcategory.displayName} $dueText"
        } else {
            val dueText = when (daysUntilDue) {
                0 -> "You have a bill due today"
                1 -> "You have a bill due tomorrow"
                else -> "You have a bill due in $daysUntilDue days"
            }
            "Bill Reminder" to dueText
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(
                if (detailed) NotificationCompat.VISIBILITY_PRIVATE
                else NotificationCompat.VISIBILITY_PUBLIC
            )
            .build()

        NotificationManagerCompat.from(context).notify(bill.id.hashCode(), notification)
    }

    /** Reminder for a credit/loan payment (when not linked to a bill). */
    @SuppressLint("MissingPermission")
    fun showDebtReminder(accountName: String, amount: Double, daysUntilDue: Int, accountId: String, detailed: Boolean) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val notificationId = "debt_$accountId".hashCode()
        val pending = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, body) = if (detailed) {
            val dueText = when (daysUntilDue) {
                0 -> "due today"
                1 -> "due tomorrow"
                else -> "due in $daysUntilDue days"
            }
            val amountStr = "$%.2f".format(amount)
            "$accountName — $amountStr" to "Payment $dueText"
        } else {
            val dueText = when (daysUntilDue) {
                0 -> "You have a payment due today"
                1 -> "You have a payment due tomorrow"
                else -> "You have a payment due in $daysUntilDue days"
            }
            "Payment Reminder" to dueText
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setVisibility(
                if (detailed) NotificationCompat.VISIBILITY_PRIVATE
                else NotificationCompat.VISIBILITY_PUBLIC
            )
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
