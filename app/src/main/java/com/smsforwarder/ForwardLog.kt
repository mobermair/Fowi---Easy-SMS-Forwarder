package com.smsforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class SendStatus { PENDING, SENT, FAILED }

enum class DeliveryStatus { PENDING, DELIVERED, FAILED }

/** Outcome of forwarding one message to one recipient. Long messages are sent in [parts]. */
data class RecipientResult(
    val number: String,
    val parts: Int = 1,
    val sendStatus: SendStatus = SendStatus.PENDING,
    val sentParts: Int = 0,
    val sentAt: Long? = null,
    val error: String? = null,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    val deliveredParts: Int = 0,
    val deliveredAt: Long? = null,
    /** Increases with every resend; status reports from earlier attempts are ignored. */
    val attempt: Int = 1,
    /** SIM the message is sent from, taken from the rule; null uses the default SMS SIM. */
    val subscriptionId: Int? = null
) {
    val failed: Boolean
        get() = sendStatus == SendStatus.FAILED || deliveryStatus == DeliveryStatus.FAILED

    /** True when the message was sent but the carrier has not reported delivery within the timeout. */
    fun deliveryReportOverdue(now: Long): Boolean =
        sendStatus == SendStatus.SENT &&
            deliveryStatus == DeliveryStatus.PENDING &&
            sentAt != null &&
            now - sentAt >= DELIVERY_REPORT_TIMEOUT_MS

    // A failed part fails the whole message, so later success reports must not overwrite it.
    fun partSent(at: Long): RecipientResult {
        if (sendStatus == SendStatus.FAILED) return this
        val sent = sentParts + 1
        return if (sent >= parts) copy(sentParts = sent, sendStatus = SendStatus.SENT, sentAt = at)
        else copy(sentParts = sent)
    }

    fun sendFailed(reason: String, at: Long): RecipientResult =
        copy(sendStatus = SendStatus.FAILED, error = reason, sentAt = at)

    fun partDelivered(at: Long): RecipientResult {
        if (deliveryStatus == DeliveryStatus.FAILED) return this
        val delivered = deliveredParts + 1
        return if (delivered >= parts) {
            copy(deliveredParts = delivered, deliveryStatus = DeliveryStatus.DELIVERED, deliveredAt = at)
        } else {
            copy(deliveredParts = delivered)
        }
    }

    fun deliveryFailed(at: Long): RecipientResult =
        copy(deliveryStatus = DeliveryStatus.FAILED, deliveredAt = at)

    fun resetForResend(parts: Int): RecipientResult =
        RecipientResult(number = number, parts = parts, attempt = attempt + 1, subscriptionId = subscriptionId)

    companion object {
        const val DELIVERY_REPORT_TIMEOUT_MS = 24 * 60 * 60 * 1000L
    }
}

/**
 * Snapshot of a rule's filters at forwarding time, so the history stays accurate after the rule
 * is edited or deleted. Entries from older versions only carry a preformatted [legacySummary].
 */
data class MatchedRule(
    val id: String,
    val name: String = "",
    val senderFilter: String = "",
    val messageFilter: String = "",
    /** The rule's recipients at that time, to show which rule a recipient got the message from. */
    val recipients: List<String> = emptyList(),
    val legacySummary: String? = null
)

data class ForwardLogEntry(
    val id: String,
    val receivedAt: Long,
    val sender: String,
    val body: String,
    val rules: List<MatchedRule>,
    val recipients: List<RecipientResult>
)

/** Local history of forwarded messages, newest first. */
object ForwardLog {
    const val PREFERENCES_NAME = "forward_log"
    private const val ENTRIES_KEY = "entries"
    private const val SENT_TOTAL_KEY = "sent_total"
    private const val MAX_ENTRIES = 200

    fun load(context: Context): List<ForwardLogEntry> {
        val stored = preferences(context).getString(ENTRIES_KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(stored) }.getOrElse { return emptyList() }
        return (0 until array.length()).mapNotNull { index ->
            runCatching { parseEntry(array.getJSONObject(index)) }.getOrNull()
        }
    }

    /**
     * Number of successfully sent SMS since install, counted per recipient and including resends.
     * Unlike the history it is never trimmed or cleared. Before this counter existed, it starts
     * from the messages recorded in the history.
     */
    fun sentTotal(context: Context): Int {
        val preferences = preferences(context)
        if (preferences.contains(SENT_TOTAL_KEY)) return preferences.getInt(SENT_TOTAL_KEY, 0)
        return load(context).sumOf { entry -> entry.recipients.count { it.sendStatus == SendStatus.SENT } }
    }

    @Synchronized
    fun add(context: Context, entry: ForwardLogEntry) {
        val entries = (listOf(entry) + load(context)).take(MAX_ENTRIES)
        preferences(context).edit().putString(ENTRIES_KEY, serialize(entries)).apply()
    }

    /** Applies [transform] to one recipient and returns its state before and after, or null if it is gone. */
    @Synchronized
    fun updateRecipient(
        context: Context,
        entryId: String,
        recipientIndex: Int,
        transform: (RecipientResult) -> RecipientResult
    ): Pair<RecipientResult, RecipientResult>? {
        val entries = load(context)
        val entry = entries.firstOrNull { it.id == entryId } ?: return null
        val before = entry.recipients.getOrNull(recipientIndex) ?: return null
        val after = transform(before)
        val updatedEntry = entry.copy(
            recipients = entry.recipients.mapIndexed { index, recipient ->
                if (index == recipientIndex) after else recipient
            }
        )
        val updatedEntries = entries.map { if (it.id == entryId) updatedEntry else it }
        val editor = preferences(context).edit().putString(ENTRIES_KEY, serialize(updatedEntries))
        if (after.sendStatus == SendStatus.SENT && before.sendStatus != SendStatus.SENT) {
            editor.putInt(SENT_TOTAL_KEY, sentTotal(context) + 1)
        }
        editor.apply()
        return before to after
    }

    /** Removes one entry and returns its former position, or -1 if it was not found. */
    @Synchronized
    fun delete(context: Context, entryId: String): Int {
        val entries = load(context)
        val index = entries.indexOfFirst { it.id == entryId }
        if (index >= 0) {
            preferences(context).edit()
                .putString(ENTRIES_KEY, serialize(entries.filterIndexed { i, _ -> i != index }))
                .apply()
        }
        return index
    }

    /** Puts a deleted entry back at its former position (undo). */
    @Synchronized
    fun restore(context: Context, entry: ForwardLogEntry, index: Int) {
        val entries = load(context).toMutableList()
        if (entries.any { it.id == entry.id }) return
        entries.add(index.coerceIn(0, entries.size), entry)
        preferences(context).edit().putString(ENTRIES_KEY, serialize(entries.take(MAX_ENTRIES))).apply()
    }

    @Synchronized
    fun clear(context: Context) {
        preferences(context).edit().remove(ENTRIES_KEY).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun serialize(entries: List<ForwardLogEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            val rules = JSONArray()
            entry.rules.forEach { rule ->
                rules.put(
                    JSONObject()
                        .put("id", rule.id)
                        .put("name", rule.name)
                        .put("senderFilter", rule.senderFilter)
                        .put("messageFilter", rule.messageFilter)
                        .put("recipients", JSONArray(rule.recipients))
                        .putOpt("summary", rule.legacySummary)
                )
            }
            val recipients = JSONArray()
            entry.recipients.forEach { recipient ->
                recipients.put(
                    JSONObject()
                        .put("number", recipient.number)
                        .put("parts", recipient.parts)
                        .put("sendStatus", recipient.sendStatus.name)
                        .put("sentParts", recipient.sentParts)
                        .putOpt("sentAt", recipient.sentAt)
                        .putOpt("error", recipient.error)
                        .put("deliveryStatus", recipient.deliveryStatus.name)
                        .put("deliveredParts", recipient.deliveredParts)
                        .putOpt("deliveredAt", recipient.deliveredAt)
                        .put("attempt", recipient.attempt)
                        .putOpt("subscriptionId", recipient.subscriptionId)
                )
            }
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("receivedAt", entry.receivedAt)
                    .put("sender", entry.sender)
                    .put("body", entry.body)
                    .put("rules", rules)
                    .put("recipients", recipients)
            )
        }
        return array.toString()
    }

    private fun parseEntry(item: JSONObject): ForwardLogEntry {
        val rules = item.getJSONArray("rules")
        val recipients = item.getJSONArray("recipients")
        return ForwardLogEntry(
            id = item.getString("id"),
            receivedAt = item.getLong("receivedAt"),
            sender = item.getString("sender"),
            body = item.getString("body"),
            rules = List(rules.length()) { index ->
                val rule = rules.getJSONObject(index)
                val ruleRecipients = rule.optJSONArray("recipients")
                MatchedRule(
                    id = rule.getString("id"),
                    name = rule.optString("name"),
                    recipients = List(ruleRecipients?.length() ?: 0) { ruleRecipients!!.getString(it) },
                    senderFilter = rule.optString("senderFilter"),
                    messageFilter = rule.optString("messageFilter"),
                    legacySummary = rule.optString("summary").takeIf { rule.has("summary") }
                )
            },
            recipients = List(recipients.length()) { index ->
                val recipient = recipients.getJSONObject(index)
                RecipientResult(
                    number = recipient.getString("number"),
                    parts = recipient.optInt("parts", 1),
                    sendStatus = enumValueOrDefault(recipient.optString("sendStatus"), SendStatus.PENDING),
                    sentParts = recipient.optInt("sentParts"),
                    sentAt = recipient.optLongOrNull("sentAt"),
                    error = recipient.optString("error").takeIf { recipient.has("error") },
                    deliveryStatus = enumValueOrDefault(
                        recipient.optString("deliveryStatus"),
                        DeliveryStatus.PENDING
                    ),
                    deliveredParts = recipient.optInt("deliveredParts"),
                    deliveredAt = recipient.optLongOrNull("deliveredAt"),
                    attempt = recipient.optInt("attempt", 1),
                    subscriptionId = if (recipient.has("subscriptionId")) recipient.getInt("subscriptionId") else null
                )
            }
        )
    }

    private fun JSONObject.optLongOrNull(name: String): Long? = if (has(name)) getLong(name) else null

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
