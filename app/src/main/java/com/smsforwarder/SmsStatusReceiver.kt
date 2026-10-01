package com.smsforwarder

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsMessage

/** Receives the sent and delivery reports for forwarded messages and records them in the [ForwardLog]. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
        val recipientIndex = intent.getIntExtra(EXTRA_RECIPIENT_INDEX, -1).takeIf { it >= 0 } ?: return
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 1)
        val now = System.currentTimeMillis()

        when (intent.action) {
            ACTION_SENT -> if (resultCode == Activity.RESULT_OK) {
                record(context, entryId, recipientIndex, attempt) { it.partSent(now) }
            } else {
                val reason = sendErrorFromResultCode(resultCode)
                record(context, entryId, recipientIndex, attempt) { it.sendFailed(reason, now) }
            }
            ACTION_DELIVERED -> when (deliveryOutcome(intent)) {
                DeliveryStatus.DELIVERED -> record(context, entryId, recipientIndex, attempt) { it.partDelivered(now) }
                DeliveryStatus.FAILED -> record(context, entryId, recipientIndex, attempt) { it.deliveryFailed(now) }
                // The carrier is still trying; a final report follows.
                DeliveryStatus.PENDING -> Unit
            }
        }
    }

    private fun record(
        context: Context,
        entryId: String,
        recipientIndex: Int,
        attempt: Int,
        transform: (RecipientResult) -> RecipientResult
    ) {
        val (before, after) = ForwardLog.updateRecipient(context, entryId, recipientIndex) {
            // A late report from an attempt that has since been resent must not change the new one.
            if (it.attempt == attempt) transform(it) else it
        } ?: return
        // Multipart messages report every part, so only alert on the first failure.
        if (after.failed && !before.failed) {
            Notifications.showForwardFailed(context, after)
        }
        if (after.sendStatus == SendStatus.SENT && before.sendStatus != SendStatus.SENT) {
            DeliveryTimeoutWorker.schedule(context, entryId, recipientIndex)
        }
    }

    private fun deliveryOutcome(intent: Intent): DeliveryStatus {
        // Without a PDU the report carries no status; its arrival alone means the message was delivered.
        val pdu = intent.getByteArrayExtra("pdu") ?: return DeliveryStatus.DELIVERED
        val format = intent.getStringExtra("format")
        val message = SmsMessage.createFromPdu(pdu, format) ?: return DeliveryStatus.DELIVERED
        return deliveryStatusFromReport(message.status, format)
    }

    companion object {
        private const val ACTION_SENT = "com.smsforwarder.SMS_SENT"
        private const val ACTION_DELIVERED = "com.smsforwarder.SMS_DELIVERED"
        private const val EXTRA_ENTRY_ID = "entryId"
        private const val EXTRA_RECIPIENT_INDEX = "recipientIndex"
        private const val EXTRA_ATTEMPT = "attempt"

        fun sentIntent(context: Context, entryId: String, recipientIndex: Int, attempt: Int, part: Int): PendingIntent =
            pendingIntent(context, ACTION_SENT, entryId, recipientIndex, attempt, part, PendingIntent.FLAG_IMMUTABLE)

        // The system adds the delivery report PDU as an extra, which requires a mutable PendingIntent.
        fun deliveryIntent(context: Context, entryId: String, recipientIndex: Int, attempt: Int, part: Int): PendingIntent =
            pendingIntent(context, ACTION_DELIVERED, entryId, recipientIndex, attempt, part, PendingIntent.FLAG_MUTABLE)

        private fun pendingIntent(
            context: Context,
            action: String,
            entryId: String,
            recipientIndex: Int,
            attempt: Int,
            part: Int,
            mutabilityFlag: Int
        ): PendingIntent {
            // PendingIntents that differ only in extras are treated as the same one, so the
            // data URI makes each message, recipient, attempt and part distinct.
            val intent = Intent(context, SmsStatusReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("relay-status://$entryId/$recipientIndex/$attempt/$part"))
                .putExtra(EXTRA_ENTRY_ID, entryId)
                .putExtra(EXTRA_RECIPIENT_INDEX, recipientIndex)
                .putExtra(EXTRA_ATTEMPT, attempt)
            return PendingIntent.getBroadcast(context, 0, intent, mutabilityFlag)
        }
    }
}

/**
 * Maps the status of an SMS status report to a delivery outcome.
 * GSM (3GPP): 0x00–0x1F completed, 0x20–0x3F still trying, 0x40+ failed.
 * CDMA (3GPP2): the error class sits in bits 24–25: 0 no error, 2 temporary, 3 permanent.
 */
internal fun deliveryStatusFromReport(status: Int, format: String?): DeliveryStatus =
    if (format == "3gpp2") {
        when ((status shr 24) and 0x03) {
            0 -> DeliveryStatus.DELIVERED
            2 -> DeliveryStatus.PENDING
            else -> DeliveryStatus.FAILED
        }
    } else {
        when {
            status < 0x20 -> DeliveryStatus.DELIVERED
            status < 0x40 -> DeliveryStatus.PENDING
            else -> DeliveryStatus.FAILED
        }
    }
