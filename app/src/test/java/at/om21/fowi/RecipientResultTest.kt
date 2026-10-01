package at.om21.fowi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientResultTest {
    @Test
    fun multipartMessageIsSentAndDeliveredOnlyAfterAllParts() {
        val pending = RecipientResult(number = "+491701234567", parts = 2)

        val halfSent = pending.partSent(at = 10)
        assertEquals(SendStatus.PENDING, halfSent.sendStatus)

        val sent = halfSent.partSent(at = 20)
        assertEquals(SendStatus.SENT, sent.sendStatus)
        assertEquals(20L, sent.sentAt)

        val delivered = sent.partDelivered(at = 30).partDelivered(at = 40)
        assertEquals(DeliveryStatus.DELIVERED, delivered.deliveryStatus)
        assertEquals(40L, delivered.deliveredAt)
        assertFalse(delivered.failed)
    }

    @Test
    fun laterSuccessReportsDoNotHideAFailedPart() {
        val failed = RecipientResult(number = "123456", parts = 2)
            .sendFailed("No service", at = 10)
            .partSent(at = 20)

        assertEquals(SendStatus.FAILED, failed.sendStatus)
        assertEquals("No service", failed.error)
        assertTrue(failed.failed)

        val undelivered = RecipientResult(number = "123456", parts = 2)
            .partSent(at = 1).partSent(at = 2)
            .deliveryFailed(at = 10)
            .partDelivered(at = 20)
        assertEquals(DeliveryStatus.FAILED, undelivered.deliveryStatus)
    }

    @Test
    fun deliveryReportIsOverdueOnlyAfterTimeoutWithoutReport() {
        val timeout = RecipientResult.DELIVERY_REPORT_TIMEOUT_MS
        val sent = RecipientResult(number = "123456").partSent(at = 1_000)

        assertFalse(sent.deliveryReportOverdue(now = 1_000 + timeout - 1))
        assertTrue(sent.deliveryReportOverdue(now = 1_000 + timeout))
        assertFalse(sent.partDelivered(at = 2_000).deliveryReportOverdue(now = 1_000 + timeout))
        assertFalse(RecipientResult(number = "123456").deliveryReportOverdue(now = 1_000 + timeout))
    }

    @Test
    fun resendStartsANewAttemptWithCleanStatus() {
        val failed = RecipientResult(number = "123456", parts = 1).sendFailed("No service", at = 10)

        val resent = failed.resetForResend(parts = 2)

        assertEquals(RecipientResult(number = "123456", parts = 2, attempt = 2), resent)
    }

    @Test
    fun mapsGsmStatusReports() {
        assertEquals(DeliveryStatus.DELIVERED, deliveryStatusFromReport(0x00, "3gpp"))
        assertEquals(DeliveryStatus.PENDING, deliveryStatusFromReport(0x20, "3gpp"))
        assertEquals(DeliveryStatus.FAILED, deliveryStatusFromReport(0x45, "3gpp"))
    }

    @Test
    fun mapsCdmaStatusReports() {
        assertEquals(DeliveryStatus.DELIVERED, deliveryStatusFromReport(0, "3gpp2"))
        assertEquals(DeliveryStatus.PENDING, deliveryStatusFromReport(2 shl 24, "3gpp2"))
        assertEquals(DeliveryStatus.FAILED, deliveryStatusFromReport(3 shl 24, "3gpp2"))
    }
}
