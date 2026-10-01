package com.smsforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

object RuleStore {
    private const val PREFERENCES_NAME = "forwarding_rules"
    private const val RULES_KEY = "rules"

    fun load(context: Context): List<ForwardRule> {
        val storedRules = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(RULES_KEY, "[]") ?: "[]"

        val array = runCatching { JSONArray(storedRules) }.getOrElse { return emptyList() }
        // Parse each rule on its own so one damaged entry does not discard all other rules.
        return (0 until array.length()).mapNotNull { index ->
            runCatching { parseRule(array.getJSONObject(index)) }.getOrNull()
        }
    }

    private fun parseRule(item: JSONObject): ForwardRule {
        val recipients = item.getJSONArray("recipients")
        return ForwardRule(
            id = item.getString("id"),
            name = item.optString("name"),
            senderFilter = item.optString("senderFilter"),
            messageFilter = item.optString("messageFilter"),
            excludeFilter = item.optString("excludeFilter"),
            useRegex = item.optBoolean("useRegex", false),
            schedule = item.optJSONObject("schedule")?.let(::parseSchedule) ?: RuleSchedule.ALWAYS,
            recipients = List(recipients.length()) { recipientIndex ->
                recipients.getString(recipientIndex)
            },
            enabled = item.optBoolean("enabled", true),
            subscriptionId = if (item.has("subscriptionId")) item.getInt("subscriptionId") else null
        )
    }

    // Days are stored as ISO numbers, 1 = Monday to 7 = Sunday.
    private fun parseSchedule(item: JSONObject): RuleSchedule {
        val days = item.getJSONArray("days")
        return RuleSchedule(
            days = List(days.length()) { DayOfWeek.of(days.getInt(it)) }.toSet(),
            startMinute = if (item.has("startMinute")) item.getInt("startMinute") else null,
            endMinute = if (item.has("endMinute")) item.getInt("endMinute") else null
        )
    }

    private fun serializeSchedule(schedule: RuleSchedule): JSONObject =
        JSONObject()
            .put("days", JSONArray(schedule.days.map(DayOfWeek::getValue).sorted()))
            .putOpt("startMinute", schedule.startMinute)
            .putOpt("endMinute", schedule.endMinute)

    fun save(context: Context, rules: List<ForwardRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            val recipients = JSONArray()
            rule.recipients.forEach(recipients::put)
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("name", rule.name)
                    .put("senderFilter", rule.senderFilter)
                    .put("messageFilter", rule.messageFilter)
                    .put("excludeFilter", rule.excludeFilter)
                    .put("useRegex", rule.useRegex)
                    .putOpt("schedule", rule.schedule.takeUnless { it.isAlways }?.let(::serializeSchedule))
                    .put("recipients", recipients)
                    .put("enabled", rule.enabled)
                    .putOpt("subscriptionId", rule.subscriptionId)
            )
        }

        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(RULES_KEY, array.toString())
            .apply()
    }
}