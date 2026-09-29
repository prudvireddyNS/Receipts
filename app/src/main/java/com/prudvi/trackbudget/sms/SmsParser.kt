package com.prudvi.trackbudget.sms

import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.ParsedTransaction
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object SmsParser {
    private val money = Regex("""(?i)(?:\b(?:inr|rs\.?)\s*|₹\s*)([\d,]+(?:\.\d{1,2})?)|([\d,]+(?:\.\d{1,2})?)\s*(?:\b(?:inr|rs\.?)\b|₹)""")
    private val transactionVerb = Regex("""(?i)\b(debited|debit(?!\s+card)|spent|paid|withdrawn|withdrawal|purchase|charged|deducted|sent|transferred|credited|credit(?!\s+card)|received|deposited|refund|refunded|reversal|reversed)\b""")
    private val debitVerb = setOf("debited", "debit", "spent", "paid", "withdrawn", "withdrawal", "purchase", "charged", "deducted", "sent", "transferred")
    private val creditVerb = setOf("credited", "credit", "received", "deposited", "refund", "refunded", "reversal", "reversed")
    private val hardNegative = Regex("""(?i)\b(otp|one[- ]time password|do not share|verification code|will be debited|is due|due on|minimum amount due|cashback offer|apply now|eligible for|pre[- ]approved|loan offer|failed|declined|unsuccessful|rejected|pending|cancelled|canceled)\b""")
    private val mandateNoise = Regex("""(?i)\b(autopay|mandate|e-mandate)\b.{0,50}\b(registration|register|created|revoke|revoked|pause|paused|will be debited|upcoming)\b|\bupcoming mandate\b""")
    private val nonInr = Regex("""(?i)(?:[${'$'}€£]|\b(?:usd|eur|gbp|aed|sgd|aud|cad)\b)""")
    private val explicitRefund = Regex("""(?i)\b(refund(?:ed)?|reversal|reversed)\b""")
    private val selfTransfer = Regex("""(?i)\b(self transfer|own account|between your accounts|from your (?:a/c|acct|account).{0,60}to your (?:a/c|acct|account))\b""")
    private val atmWithdrawal = Regex("""(?i)\b(atm|cash)\b.{0,30}\b(withdrawn|withdrawal)\b|\b(withdrawn|withdrawal)\b.{0,30}\b(atm|cash)\b""")
    private val investment = Regex("""(?i)\b(mutual fund|sip|nps|zerodha|groww|upstox|demat|investment)\b""")
    private val cardRepayment = Regex("""(?i)\b(?:credit card|card bill|cc)\b.{0,80}\b(?:bill payment|payment received|repayment|payment of)\b|\b(?:bill payment|repayment)\b.{0,80}\b(?:credit card|card bill|cc)\b|\bsent from a/c\b.{0,60}\bto\s+["']?\w+\s+small fi\b""")
    private val balance = Regex("""(?i)\b(?:avl|available|clear|closing)?\s*bal(?:ance)?[^\d]{0,12}(?:inr|rs\.?|₹)?\s*([\d,]+(?:\.\d{1,2})?)""")
    private val account = Regex("""(?i)\b(?:a/c|acct|account|card|ac)\b[^\d]{0,10}(?:[xX*]+)?(\d{3,6})|\b[xX*]{2,}(\d{3,6})\b""")
    private val reference = Regex("""(?i)\b(?:ref|rrn|utr|txn|transaction)\s*(?:no\.?|id|#)?[:\s]*((?=[A-Za-z0-9]*\d)[A-Za-z0-9]{6,22})\b""")
    private val debitMerchantPatterns = listOf(
        Regex("""(?i)\b(?:paid\s+to|sent\s+to|transferred\s+to|trf\s+to|at|to|towards|in favour of)\s+(?:(?:upi(?:\s+id)?|vpa)\s*[:\-]?\s*)?["']?([A-Za-z0-9@._&' -]{2,64}?)(?=\s+(?:on|ref|upi|rrn|txn|utr|avl|bal|dt|date|using|via|from|a/c|acct|account)\b|[().,;]|$)"""),
        Regex("""(?i)\binfo[:\s]+([A-Za-z0-9@._&' -]{3,40})"""),
        Regex("""(?i)\b([\w.-]{2,64}@[a-z]{2,64})\b"""),
    )
    private val creditMerchantPatterns = listOf(
        Regex("""(?i)\bfrom\s+(?:(?:upi(?:\s+id)?|vpa)\s*[:\-]?\s*)?["']?([A-Za-z0-9@._&' -]{2,64}?)(?=\s+(?:on|ref|upi|rrn|txn|utr|avl|bal|dt|date|using|via|to|a/c|acct|account)\b|[().,;]|$)"""),
        Regex("""(?i)\b([\w.-]{2,64}@[a-z]{2,64})\b"""),
    )

    fun senderKey(sender: String): String? {
        val normalized = sender.trim().uppercase()
        if (normalized.count(Char::isDigit) >= 7) return null
        val parts = normalized.split('-')
        val entity = if (parts.size >= 2) parts[1] else parts[0]
        return entity.filter(Char::isLetterOrDigit).takeIf { it.length in 3..15 }
    }

    fun isPaymentCandidate(sender: String, body: String): Boolean {
        if (senderKey(sender) == null && sender.count(Char::isDigit) !in 5..6) return false
        if (nonInr.containsMatchIn(body) || hardNegative.containsMatchIn(body) || mandateNoise.containsMatchIn(body)) return false
        val completedTransaction = Regex(
            "(?i)\\b(debited|debit(?!\\s+card)|credited|credit(?!\\s+card)|received|deposited|spent|paid|withdrawn|withdrawal|purchase|charged|deducted|transferred|refund|refunded|reversal|reversed)\\b",
        )
        return completedTransaction.containsMatchIn(body) && money.containsMatchIn(body)
    }

    fun parse(sender: String, body: String, receivedAt: Long): ParsedTransaction? {
        if (senderKey(sender) == null && sender.count(Char::isDigit) !in 5..6) return null
        if (nonInr.containsMatchIn(body) || hardNegative.containsMatchIn(body) || mandateNoise.containsMatchIn(body)) return null

        val verbs = transactionVerb.findAll(body).toList()
        if (verbs.isEmpty()) return null
        val balanceSpans = balance.findAll(body).map { it.range }.toList()
        val amounts = money.findAll(body)
            .filter { match -> balanceSpans.none { span -> match.range.first in span } }
            .mapNotNull { match ->
                val raw = match.groupValues[1].ifBlank { match.groupValues[2] }.replace(",", "")
                raw.toBigDecimalOrNull()?.let { match to it }
            }
            .toList()
        if (amounts.isEmpty()) return null

        val selected = amounts.minBy { (amountMatch, _) ->
            verbs.minOf { verb -> kotlin.math.abs(verb.range.first - amountMatch.range.first) }
        }
        val nearestVerb = verbs.minBy { kotlin.math.abs(it.range.first - selected.first.range.first) }.value.lowercase()
        val direction = when (nearestVerb) {
            in debitVerb -> Direction.DEBIT
            in creditVerb -> Direction.CREDIT
            else -> return null
        }

        val amountMinor = selected.second.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        if (amountMinor <= 0) return null
        val rawMerchant = merchantFromBody(body, direction)
        val merchant = normalizeMerchant(rawMerchant.orEmpty()).ifBlank {
            if (direction == Direction.CREDIT) "Money received" else "Uncategorised payment"
        }
        val tailMatch = account.find(body)
        val accountTail = tailMatch?.groupValues?.drop(1)?.firstOrNull(String::isNotBlank)
        val refId = reference.find(body)?.groupValues?.get(1)?.uppercase()
        var confidence = 0.55f
        if (accountTail != null) confidence += 0.15f
        if (rawMerchant != null) confidence += 0.15f
        if (refId != null) confidence += 0.1f
        if (amounts.size > 1) confidence -= 0.35f

        return ParsedTransaction(
            amountMinor = amountMinor,
            direction = direction,
            merchant = merchant,
            accountTail = accountTail,
            refId = refId,
            occurredAt = if (direction == Direction.CREDIT && explicitRefund.containsMatchIn(body)) receivedAt else transactionTime(body, receivedAt),
            confidence = confidence.coerceIn(0f, 1f),
            suggestedCategoryId = suggestedCategory(body, merchant, direction),
            isExplicitRefund = direction == Direction.CREDIT && explicitRefund.containsMatchIn(body),
            excludeByDefault = shouldExcludeByDefault(body, direction),
        )
    }

    private fun suggestedCategory(body: String, merchant: String, direction: Direction): String? {
        val text = "$body $merchant"
        return when {
            direction == Direction.CREDIT && explicitRefund.containsMatchIn(body) -> "refund"
            cardRepayment.containsMatchIn(body) -> "repayments"
            selfTransfer.containsMatchIn(body) -> "transfers"
            atmWithdrawal.containsMatchIn(body) -> "cash"
            direction == Direction.DEBIT && investment.containsMatchIn(text) -> "investment"
            direction == Direction.DEBIT && Regex("""(?i)\b(loan|emi)\b""").containsMatchIn(text) -> "bills"
            else -> null
        }
    }

    private fun shouldExcludeByDefault(body: String, direction: Direction): Boolean =
        cardRepayment.containsMatchIn(body) ||
            (selfTransfer.containsMatchIn(body) && !(direction == Direction.CREDIT && explicitRefund.containsMatchIn(body))) ||
            (direction == Direction.DEBIT && atmWithdrawal.containsMatchIn(body))

    private fun transactionTime(body: String, receivedAt: Long): Long {
        val zone = ZoneId.systemDefault()
        val received = Instant.ofEpochMilli(receivedAt).atZone(zone)
        val numeric = Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b""").find(body)
        val date = numeric?.let { match ->
            val year = match.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            runCatching { LocalDate.of(year, match.groupValues[2].toInt(), match.groupValues[1].toInt()) }.getOrNull()
        } ?: Regex("""(?i)\b(\d{1,2}\s+[A-Za-z]{3}\s+\d{2,4})\b""").find(body)?.groupValues?.get(1)?.let { value ->
            listOf("d MMM yyyy", "d MMM yy").firstNotNullOfOrNull { pattern ->
                runCatching { LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)) }.getOrNull()
            }
        } ?: return receivedAt
        if (date.isAfter(received.toLocalDate().plusDays(1))) return receivedAt
        val timeMatch = Regex("""(?i)\b(\d{1,2}):(\d{2})(?::\d{2})?\s*(am|pm)?\b""").find(body)
        val time = timeMatch?.let { match ->
            var hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            when (match.groupValues[3].lowercase()) {
                "am" -> if (hour == 12) hour = 0
                "pm" -> if (hour < 12) hour += 12
            }
            runCatching { LocalTime.of(hour, minute) }.getOrNull()
        } ?: received.toLocalTime()
        return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    }

    fun merchantFromBody(body: String, direction: Direction): String? {
        val patterns = if (direction == Direction.DEBIT) debitMerchantPatterns else creditMerchantPatterns
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(body)?.groupValues?.getOrNull(1)?.takeIf { candidate ->
                val normalized = normalizeMerchant(candidate)
                normalized.isNotBlank() &&
                    !normalized.startsWith("a c ") &&
                    !normalized.startsWith("account ") &&
                    !normalized.startsWith("card ") &&
                    normalized !in setOf("your account", "your a c", "upi")
            }
        }
    }

    fun normalizeMerchant(raw: String): String = raw.lowercase()
        .replace(Regex("""\b(pvt|ltd|limited|inc|llp|india|bangalore|mumbai|delhi)\b"""), "")
        .replace(Regex("""[^a-z0-9@ ]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .let { if ('@' in it) it.substringBefore('@') else it }
        .take(40)
        .split(' ')
        .joinToString(" ") { word -> word.replaceFirstChar(Char::titlecase) }
}
