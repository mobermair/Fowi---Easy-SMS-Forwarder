package com.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.time.LocalDateTime
import java.util.UUID

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!AppSettings.isForwardingEnabled(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)?.filterNotNull().orEmpty()
        val sender = messages.firstOrNull()?.originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        if (body.isBlank()) return
        if (SmsSender.isForwarded(body)) return

        val now = LocalDateTime.now()
        val matchingRules = RuleStore.load(context).filter { rule ->
            rule.enabled && rule.isActiveAt(now) && rule.matches(sender, body)
        }
        if (matchingRules.isEmpty()) return

        val message = SmsSender.messageFor(context, sender, body)
        val parts = SmsSender.partCount(context, message)
        val recipients = recipientTargets(matchingRules)

        // Log the entry before sending so the status reports always find it.
        val entry = ForwardLogEntry(
            id = UUID.randomUUID().toString(),
            receivedAt = System.currentTimeMillis(),
            sender = sender,
            body = body,
            rules = matchingRules.map {
                MatchedRule(
                    id = it.id,
                    name = it.name,
                    senderFilter = it.senderFilter,
                    messageFilter = it.messageFilter,
                    recipients = it.recipients
                )
            },
            recipients = recipients.map {
                RecipientResult(number = it.number, parts = parts, subscriptionId = it.subscriptionId)
            }
        )
        ForwardLog.add(context, entry)

        entry.recipients.forEachIndexed { index, recipient ->
            SmsSender.send(context, entry.id, index, recipient, message)
        }
    }
}
