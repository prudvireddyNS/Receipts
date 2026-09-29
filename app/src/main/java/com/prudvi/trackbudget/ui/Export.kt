package com.prudvi.trackbudget.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.prudvi.trackbudget.BuildConfig
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.category
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ExportColumns = listOf(
    "date", "time", "amount_inr", "direction", "merchant", "category", "status", "source",
    "account_tail", "sender", "reference", "raw_message",
)

/**
 * Every receipt as CSV, newest first. The app keeps no copy anywhere else (backup is off and it has
 * no network), so this is the only way to keep or move the data.
 */
fun receiptsCsv(transactions: List<Transaction>, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = DateTimeFormatter.ISO_LOCAL_DATE
    val time = DateTimeFormatter.ofPattern("HH:mm")
    return buildString {
        append(ExportColumns.joinToString(",")).append("\r\n")
        transactions.sortedByDescending { it.occurredAt }.forEach { t ->
            val moment = Instant.ofEpochMilli(t.occurredAt).atZone(zone)
            val rupees = "${t.amountMinor / 100}.${(t.amountMinor % 100).toString().padStart(2, '0')}"
            append(
                listOf(
                    date.format(moment), time.format(moment), rupees, t.direction.name.lowercase(),
                    t.merchant, category(t.categoryId)?.name ?: t.categoryId.orEmpty(),
                    t.status.name.lowercase(), t.source.name.lowercase(),
                    t.accountTail.orEmpty(), t.sender.orEmpty(), t.refId.orEmpty(), t.rawMessage.orEmpty(),
                ).joinToString(",") { csvCell(it) },
            ).append("\r\n")
        }
    }
}

/** Quotes a cell, and defuses anything a spreadsheet would run as a formula. */
internal fun csvCell(value: String): String {
    val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
    return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
}

/** Writes the CSV to the share cache and opens the system share sheet. */
fun exportReceipts(context: Context, transactions: List<Transaction>) {
    val directory = File(context.cacheDir, "receipts-share").apply { mkdirs() }
    pruneShareCache(directory)
    val file = File(directory, "receipts-${System.currentTimeMillis()}.csv")
    file.writeText(receiptsCsv(transactions), Charsets.UTF_8)
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        clipData = ClipData.newUri(context.contentResolver, "Receipts", uri)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Export receipts").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

/** Shared files hold real spending; don't leave yesterday's export or recap image lying in the cache. */
internal fun pruneShareCache(directory: File, olderThanMillis: Long = 60 * 60 * 1000L) {
    val cutoff = System.currentTimeMillis() - olderThanMillis
    directory.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
}
