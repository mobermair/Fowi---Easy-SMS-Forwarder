package com.smsforwarder

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

object SmsSender {
    // Sent to the recipient as-is, independent of the app language.
    private const val FORWARD_NOTE = "This is a forwarded message"
    private const val SENDER_PREFIX = "From: "

    /** The text sent to recipients: an optional sender line, the original text and the forward note. */
    fun forwardedText(body: String, senderLabel: String? = null): String =
        if (senderLabel == null) "$body\n\n$FORWARD_NOTE" else "$SENDER_PREFIX$senderLabel\n$body\n\n$FORWARD_NOTE"

    /** "Name (+43…)" when the sender is a contact, otherwise the sender as received. */
    fun senderLabel(sender: String, contactName: String?): String =
        if (contactName == null) sender else "$contactName ($sender)"

    /** Builds the forwarded text with the current settings; the sender line is added unless disabled. */
    fun messageFor(context: Context, sender: String, body: String): String {
        if (!AppSettings.isSenderIncluded(context)) return forwardedText(body)
        return forwardedText(body, senderLabel(sender, ContactNames.lookup(context, sender)))
    }

    /**
     * True for messages this app (or another device running it) forwarded. Such messages are
     * never forwarded again, otherwise a recipient pointing back to a relay causes an SMS loop.
     */
    fun isForwarded(body: String): Boolean = body.trimEnd().endsWith(FORWARD_NOTE)

    fun partCount(context: Context, message: String): Int =
        smsManager(context).divideMessage(message).size

    /** Sends [message], the complete text from [messageFor]. */
    fun send(context: Context, entryId: String, recipientIndex: Int, recipient: RecipientResult, message: String) {
        // Checked in this order so a missing SIM does not use up the daily limit.
        when {
            !SimCards.isAvailable(context, recipient.subscriptionId) ->
                return fail(context, entryId, recipientIndex, ERROR_SIM_UNAVAILABLE)
            !DailyLimit.tryReserve(context, recipient.parts) ->
                return fail(context, entryId, recipientIndex, ERROR_DAILY_LIMIT)
        }
        val smsManager = smsManager(context, recipient.subscriptionId)
        val parts = smsManager.divideMessage(message)
        val sentIntents = ArrayList(parts.indices.map {
            SmsStatusReceiver.sentIntent(context, entryId, recipientIndex, recipient.attempt, it)
        })
        val deliveryIntents = ArrayList(parts.indices.map {
            SmsStatusReceiver.deliveryIntent(context, entryId, recipientIndex, recipient.attempt, it)
        })
        try {
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(recipient.number, null, parts, sentIntents, deliveryIntents)
            } else {
                smsManager.sendTextMessage(recipient.number, null, message, sentIntents[0], deliveryIntents[0])
            }
        } catch (e: Exception) {
            fail(context, entryId, recipientIndex, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fail(context: Context, entryId: String, recipientIndex: Int, reason: String) {
        val result = ForwardLog.updateRecipient(context, entryId, recipientIndex) {
            it.sendFailed(reason, System.currentTimeMillis())
        }
        result?.let { (_, failed) -> Notifications.showForwardFailed(context, failed) }
    }

    /** Resets the recipient's status and sends the message again as a new attempt. */
    fun resend(context: Context, entry: ForwardLogEntry, recipientIndex: Int) {
        val message = messageFor(context, entry.sender, entry.body)
        val parts = partCount(context, message)
        val (_, reset) = ForwardLog.updateRecipient(context, entry.id, recipientIndex) {
            it.resetForResend(parts)
        } ?: return
        send(context, entry.id, recipientIndex, reset, message)
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context, subscriptionId: Int? = null): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val default = context.getSystemService(SmsManager::class.java)
            if (subscriptionId == null) default else default.createForSubscriptionId(subscriptionId)
        } else {
            if (subscriptionId == null) SmsManager.getDefault() else SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
}

// Send errors detected by the app itself, stored as codes like the system's result codes.
internal const val ERROR_DAILY_LIMIT = "daily_limit"
internal const val ERROR_SIM_UNAVAILABLE = "sim_unavailable"

private const val RESULT_CODE_PREFIX = "result:"

/** Send errors reported by the system are stored as codes so they can be shown in the current language. */
internal fun sendErrorFromResultCode(resultCode: Int): String = "$RESULT_CODE_PREFIX$resultCode"

internal fun describeSendError(context: Context, error: String?): String {
    when (error) {
        null -> return context.getString(R.string.error_unknown)
        ERROR_DAILY_LIMIT -> return context.getString(R.string.error_daily_limit, DailyLimit.limit(context))
        ERROR_SIM_UNAVAILABLE -> return context.getString(R.string.error_sim_unavailable)
    }
    val code = error.removePrefix(RESULT_CODE_PREFIX).toIntOrNull()
    if (!error.startsWith(RESULT_CODE_PREFIX) || code == null) return error
    return when (code) {
        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> context.getString(R.string.error_generic_failure)
        SmsManager.RESULT_ERROR_NO_SERVICE -> context.getString(R.string.error_no_service)
        SmsManager.RESULT_ERROR_RADIO_OFF -> context.getString(R.string.error_radio_off)
        SmsManager.RESULT_ERROR_NULL_PDU -> context.getString(R.string.error_invalid_message)
        else -> context.getString(R.string.error_code, code)
    }
}
