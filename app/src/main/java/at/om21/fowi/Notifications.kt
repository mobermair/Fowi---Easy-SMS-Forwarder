package at.om21.fowi

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifications {
    private const val FAILURE_CHANNEL_ID = "forwarding_failures"

    fun showForwardFailed(context: Context, recipient: RecipientResult) {
        val name = ContactNames.displayName(context, recipient.number)
        val text = if (recipient.sendStatus == SendStatus.FAILED) {
            context.getString(R.string.notification_send_failed_text, name, describeSendError(context, recipient.error))
        } else {
            context.getString(R.string.notification_not_delivered_text, name)
        }
        show(context, recipient, context.getString(R.string.notification_failed_title), text)
    }

    fun showDeliveryReportMissing(context: Context, recipient: RecipientResult) {
        show(
            context,
            recipient,
            context.getString(R.string.notification_unconfirmed_title),
            context.getString(R.string.notification_unconfirmed_text, ContactNames.displayName(context, recipient.number))
        )
    }

    // Notification permission is checked through areNotificationsEnabled(), which also
    // covers users who turned notifications off on Android versions before 13.
    @SuppressLint("MissingPermission")
    private fun show(context: Context, recipient: RecipientResult, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val channel = NotificationChannel(
            FAILURE_CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val openHistory = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_HISTORY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        manager.notify(
            recipient.number.hashCode(),
            NotificationCompat.Builder(context, FAILURE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openHistory)
                .setAutoCancel(true)
                .build()
        )
    }
}
