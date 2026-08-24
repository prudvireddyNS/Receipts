package com.prudvi.trackbudget.sms

import com.prudvi.trackbudget.model.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {
    private val now = 1_776_000_000_000L

    @Test
    fun parsesUpiDebitAndIgnoresBalanceAmount() {
        val result = SmsParser.parse(
            "VM-IPPBNK",
            "A/C X2771 Debit Rs.500.00 for UPI to chinthamani4pt on 31-01-26 Ref 097093697099. Avl Bal Rs.14216.95.",
            now,
        )

        requireNotNull(result)
        assertEquals(50_000, result.amountMinor)
        assertEquals(Direction.DEBIT, result.direction)
        assertEquals("2771", result.accountTail)
        assertEquals("097093697099", result.refId)
    }

    @Test
    fun usesPayeeAfterToAsDebitTitle() {
        val result = SmsParser.parse(
            "HDFCBK",
            "Rs 200.00 debited from a/c XX1234 to Blinkit Commerce via UPI Ref 452312345678.",
            now,
        )

        requireNotNull(result)
        assertEquals("Blinkit Commerce", result.merchant)
    }

    @Test
    fun parsesPayeeBeforeParenthesizedUpiReference() {
        val result = SmsParser.parse(
            "HDFCBK",
            "Rs 163.00 debited from a/c XX1234 to Blinkit Commerce (UPI Ref: 452312345680). Not you? call support.",
            now,
        )

        requireNotNull(result)
        assertEquals("Blinkit Commerce", result.merchant)
    }

    @Test
    fun stripsUpiPrefixFromPayeeAfterTo() {
        val result = SmsParser.parse(
            "HDFCBK",
            "Rs 240.00 debited from a/c XX1234 to UPI ID: mohan@ybl on 24-08-26 Ref 452312345679.",
            now,
        )

        requireNotNull(result)
        assertEquals("Mohan", result.merchant)
    }

    @Test
    fun parsesCreditForImmediateResolution() {
        val result = SmsParser.parse(
            "AD-HDFCBK",
            "Rs 1500.00 credited to a/c XX2771 from P SASI KUMAR UPI Ref 334455667788",
            now,
        )

        requireNotNull(result)
        assertEquals(Direction.CREDIT, result.direction)
        assertEquals(150_000, result.amountMinor)
        assertTrue(result.merchant.contains("Sasi", ignoreCase = true))
    }

    @Test
    fun rejectsOtp() {
        assertNull(SmsParser.parse("HDFCBK", "OTP 552211 for purchase of Rs 500. Do not share.", now))
    }

    @Test
    fun rejectsMandateRegistration() {
        assertNull(
            SmsParser.parse(
                "slice",
                "Received UPI AutoPay registration request from Shopify for Rs. 5,000.",
                now,
            ),
        )
    }

    @Test
    fun rejectsCardRepaymentConfirmation() {
        assertNull(
            SmsParser.parse(
                "slice",
                "Repayment of Rs.1,000 received for the slice credit card. The amount has been credited.",
                now,
            ),
        )
    }

    @Test
    fun rejectsCardBillDebit() {
        assertNull(
            SmsParser.parse(
                "HDFCBK",
                "Your credit card bill payment of Rs 12,000 was debited from a/c XX1234.",
                now,
            ),
        )
    }

    @Test
    fun transactionFraudLinkDoesNotCauseFalseNegative() {
        val result = SmsParser.parse(
            "HDFCBK",
            "Rs 420.00 debited from a/c XX1234 to SWIGGY UPI Ref 452312345678. Not you? click here.",
            now,
        )
        requireNotNull(result)
        assertEquals(42_000, result.amountMinor)
    }

    @Test
    fun identifiesPaymentLikeMessageThatCannotBeParsed() {
        assertTrue(
            SmsParser.isPaymentCandidate(
                "HDFCBK",
                "Payment update: transaction pending confirmation on account ending 1234, reference unavailable.",
            ),
        )
        assertNull(
            SmsParser.parse(
                "HDFCBK",
                "Payment update: transaction pending confirmation on account ending 1234, reference unavailable.",
                now,
            ),
        )
    }

    @Test
    fun serviceAndMarketingMessagesAreNotPaymentCandidates() {
        val messages = listOf(
            "Make bill payments, shop for vouchers, book EMIs, increase your credit limit on the SBI Card app.",
            "Your SBI Card sent via Delhivery will be attempted today. Provide the delivery authentication code.",
            "Your HDFC Bank Credit Card was returned due to an incorrect address.",
        )

        messages.forEach { assertFalse(SmsParser.isPaymentCandidate("HDFCBK", it)) }
    }

    @Test
    fun rejectsPersonalPhoneNumberSender() {
        assertNull(SmsParser.parse("9876543210", "Rs 500 paid to Alex Ref 12345678", now))
    }

    @Test
    fun ambiguousAmountsLowerConfidence() {
        val result = SmsParser.parse(
            "HDFCBK",
            "Rs 500 debited and Rs 600 charged to card XX1234 at STORE Ref 12345678",
            now,
        )
        requireNotNull(result)
        assertTrue(result.confidence < 0.75f)
    }
}
