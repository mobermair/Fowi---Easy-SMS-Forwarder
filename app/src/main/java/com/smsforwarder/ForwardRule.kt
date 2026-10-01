package com.smsforwarder

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.UUID

/**
 * When a rule is active. Without times it applies all day on [days]. A window whose end is before
 * its start runs past midnight, e.g. 22:00–06:00; the hours after midnight belong to the day it started.
 */
data class RuleSchedule(
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    /** Minutes after midnight; both null means all day. */
    val startMinute: Int? = null,
    val endMinute: Int? = null
) {
    val isAlways: Boolean
        get() = days.size == DayOfWeek.entries.size && startMinute == null

    fun isActiveAt(time: LocalDateTime): Boolean {
        val day = time.dayOfWeek
        val minute = time.hour * 60 + time.minute
        if (startMinute == null || endMinute == null) return day in days
        return if (startMinute < endMinute) {
            day in days && minute >= startMinute && minute < endMinute
        } else {
            (day in days && minute >= startMinute) || (day.minus(1) in days && minute < endMinute)
        }
    }

    companion object {
        val ALWAYS = RuleSchedule()
    }
}

data class ForwardRule(
    val id: String = UUID.randomUUID().toString(),
    /** Optional label; without it, the rule is described by its filters. */
    val name: String = "",
    /** In plain mode, each filter is a comma-separated list of terms of which one must occur. */
    val senderFilter: String = "",
    val messageFilter: String = "",
    /** Messages whose text matches this filter are not forwarded, even if the other filters match. */
    val excludeFilter: String = "",
    /** Treats all three filters as regular expressions instead of term lists. */
    val useRegex: Boolean = false,
    val schedule: RuleSchedule = RuleSchedule.ALWAYS,
    val recipients: List<String> = emptyList(),
    val enabled: Boolean = true,
    /** SIM the forwards are sent from; null uses the phone's default SMS SIM. */
    val subscriptionId: Int? = null
) {
    /** Checks the filters only; see [isActiveAt] for the schedule. */
    fun matches(sender: String, body: String): Boolean {
        val senderMatches = senderFilter.isBlank() || filterMatches(senderFilter, sender, useRegex)
        val messageMatches = messageFilter.isBlank() || filterMatches(messageFilter, body, useRegex)
        val excluded = excludeFilter.isNotBlank() && filterMatches(excludeFilter, body, useRegex)
        return senderMatches && messageMatches && !excluded
    }

    fun isActiveAt(time: LocalDateTime): Boolean = schedule.isActiveAt(time)
}

/** Splits a plain filter into its comma-separated terms. */
fun filterTerms(filter: String): List<String> = filter.split(',').map(String::trim).filter(String::isNotEmpty)

/** True if [filter] is a valid regular expression; blank filters count as valid. */
fun isValidRegex(filter: String): Boolean = filter.isBlank() || runCatching { filterRegex(filter) }.isSuccess

private fun filterRegex(filter: String) = Regex(filter.trim(), RegexOption.IGNORE_CASE)

// Upper and lower case never matter. An invalid expression matches nothing, so a broken rule
// stays quiet instead of forwarding everything.
private fun filterMatches(filter: String, value: String, useRegex: Boolean): Boolean =
    if (useRegex) {
        runCatching { filterRegex(filter).containsMatchIn(value) }.getOrDefault(false)
    } else {
        filterTerms(filter).any { value.contains(it, ignoreCase = true) }
    }

/** A recipient of a forward and the SIM it is sent from (null for the default SIM). */
data class RecipientTarget(val number: String, val subscriptionId: Int?)

/**
 * The recipients of all [matchingRules]. A number that appears in several rules gets the message
 * only once, through the SIM of the first of those rules.
 */
fun recipientTargets(matchingRules: List<ForwardRule>): List<RecipientTarget> =
    matchingRules
        .flatMap { rule -> rule.recipients.map { RecipientTarget(it, rule.subscriptionId) } }
        .distinctBy(RecipientTarget::number)

private val phoneSeparators = Regex("[\\s()./-]")
private val phoneNumber = Regex("^\\+?\\d{6,15}$")

/**
 * Removes the separators people commonly type or store in contacts ("0171/123 45-67")
 * and returns the number, or null if it is not a plausible phone number.
 */
fun normalizePhoneNumber(input: String): String? =
    input.replace(phoneSeparators, "").takeIf(phoneNumber::matches)
