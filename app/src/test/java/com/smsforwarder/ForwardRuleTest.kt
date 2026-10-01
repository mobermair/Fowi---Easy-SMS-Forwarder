package com.smsforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ForwardRuleTest {
    @Test
    fun matchesSenderAndMessageWithoutCaseSensitivity() {
        val rule = ForwardRule(senderFilter = "ACME", messageFilter = "verification")

        assertTrue(rule.matches("Acme Bank", "Your VERIFICATION code is 1234"))
        assertFalse(rule.matches("Other Bank", "Your verification code is 1234"))
        assertFalse(rule.matches("Acme Bank", "Your balance is 10"))
    }

    @Test
    fun allowsEitherFilterToBeOptional() {
        val senderOnly = ForwardRule(senderFilter = "Acme")
        val messageOnly = ForwardRule(messageFilter = "receipt")

        assertTrue(senderOnly.matches("Acme Bank", "Anything"))
        assertTrue(messageOnly.matches("Any sender", "Your receipt is ready"))
    }

    @Test
    fun matchesAnyOfSeveralCommaSeparatedTerms() {
        val rule = ForwardRule(messageFilter = "code, TAN ,PIN")

        assertTrue(rule.matches("Bank", "Ihre tan lautet 1234"))
        assertTrue(rule.matches("Bank", "Your PIN"))
        assertFalse(rule.matches("Bank", "Ihr Saldo: 100 EUR"))
    }

    @Test
    fun skipsMessagesThatContainTheExcludeFilter() {
        val rule = ForwardRule(senderFilter = "Bank", excludeFilter = "Werbung, Angebot")

        assertTrue(rule.matches("Bank", "Ihre TAN 1234"))
        assertFalse(rule.matches("Bank", "Unser ANGEBOT für Sie"))
    }

    @Test
    fun evaluatesRegexFiltersAndNeverMatchesInvalidOnes() {
        val rule = ForwardRule(messageFilter = "\\b\\d{6}\\b", useRegex = true)

        assertTrue(rule.matches("Any", "Code: 482913"))
        assertFalse(rule.matches("Any", "Code: 48291"))
        assertFalse(ForwardRule(messageFilter = "([", useRegex = true).matches("Any", "(["))
        assertFalse(isValidRegex("(["))
        assertTrue(isValidRegex(""))
    }

    @Test
    fun appliesSchedulesIncludingWindowsPastMidnight() {
        val monday = LocalDateTime.of(2026, 9, 28, 0, 0)
        val workdays = RuleSchedule(days = WORKDAYS, startMinute = 8 * 60, endMinute = 18 * 60)
        assertTrue(workdays.isActiveAt(monday.withHour(8)))
        assertFalse(workdays.isActiveAt(monday.withHour(18)))
        assertFalse(workdays.isActiveAt(monday.plusDays(5).withHour(10)))

        // Friday 22:00 to Saturday 06:00 belongs to Friday.
        val fridayNights = RuleSchedule(days = setOf(DayOfWeek.FRIDAY), startMinute = 22 * 60, endMinute = 6 * 60)
        assertTrue(fridayNights.isActiveAt(monday.plusDays(4).withHour(23)))
        assertTrue(fridayNights.isActiveAt(monday.plusDays(5).withHour(5)))
        assertFalse(fridayNights.isActiveAt(monday.withHour(5)))

        assertTrue(RuleSchedule.ALWAYS.isActiveAt(monday))
        assertTrue(RuleSchedule(days = WEEKEND).isActiveAt(monday.plusDays(6).withHour(12)))
    }

    @Test
    fun normalizesPhoneNumbers() {
        assertEquals("01711234567", normalizePhoneNumber("0171/123 45-67"))
        assertEquals("+491711234567", normalizePhoneNumber("+49 (171) 123.45.67"))
        assertNull(normalizePhoneNumber("------"))
        assertNull(normalizePhoneNumber("12345"))
        assertNull(normalizePhoneNumber("+49 171 abc"))
    }

    @Test
    fun recognizesForwardedMessages() {
        val forwarded = SmsSender.forwardedText("Your code is 1234")

        assertEquals("Your code is 1234\n\nThis is a forwarded message", forwarded)
        assertTrue(SmsSender.isForwarded(forwarded))
        assertTrue(SmsSender.isForwarded("$forwarded \n"))
        assertFalse(SmsSender.isForwarded("Your code is 1234"))
    }

    @Test
    fun sendsToEachRecipientOnceThroughTheFirstRulesSim() {
        val first = ForwardRule(messageFilter = "a", recipients = listOf("+111111", "+222222"), subscriptionId = 1)
        val second = ForwardRule(messageFilter = "a", recipients = listOf("+222222", "+333333"))

        assertEquals(
            listOf(RecipientTarget("+111111", 1), RecipientTarget("+222222", 1), RecipientTarget("+333333", null)),
            recipientTargets(listOf(first, second))
        )
    }

    @Test
    fun dailyLimitCountsAllPartsAndZeroMeansNoLimit() {
        assertTrue(DailyLimit.allows(limit = 100, sentToday = 98, parts = 2))
        assertFalse(DailyLimit.allows(limit = 100, sentToday = 99, parts = 2))
        assertTrue(DailyLimit.allows(limit = 0, sentToday = 5000, parts = 3))
    }

    @Test
    fun startsWithSenderLineWhenGiven() {
        val forwarded = SmsSender.forwardedText("Your code is 1234", SmsSender.senderLabel("+436641234567", "Bank"))

        assertEquals("From: Bank (+436641234567)\nYour code is 1234\n\nThis is a forwarded message", forwarded)
        assertTrue(SmsSender.isForwarded(forwarded))
        assertEquals("ACME", SmsSender.senderLabel("ACME", null))
    }
}