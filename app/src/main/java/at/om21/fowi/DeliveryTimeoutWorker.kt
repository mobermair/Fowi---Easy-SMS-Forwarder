package at.om21.fowi

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Alerts the user when a sent message still has no delivery report after the timeout. */
class DeliveryTimeoutWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val entryId = inputData.getString(KEY_ENTRY_ID) ?: return Result.success()
        val recipientIndex = inputData.getInt(KEY_RECIPIENT_INDEX, -1)
        val recipient = ForwardLog.load(applicationContext)
            .firstOrNull { it.id == entryId }
            ?.recipients
            ?.getOrNull(recipientIndex)
            ?: return Result.success()

        if (recipient.deliveryReportOverdue(System.currentTimeMillis())) {
            Notifications.showDeliveryReportMissing(applicationContext, recipient)
        }
        return Result.success()
    }

    companion object {
        private const val KEY_ENTRY_ID = "entryId"
        private const val KEY_RECIPIENT_INDEX = "recipientIndex"

        fun schedule(context: Context, entryId: String, recipientIndex: Int) {
            val request = OneTimeWorkRequestBuilder<DeliveryTimeoutWorker>()
                .setInitialDelay(RecipientResult.DELIVERY_REPORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ENTRY_ID to entryId, KEY_RECIPIENT_INDEX to recipientIndex))
                .build()
            // A resend restarts the 24-hour window, so it replaces the check of the earlier attempt.
            WorkManager.getInstance(context).enqueueUniqueWork(
                "delivery-timeout-$entryId-$recipientIndex",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
