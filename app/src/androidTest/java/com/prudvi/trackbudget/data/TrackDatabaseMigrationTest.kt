package com.prudvi.trackbudget.data

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionSource
import com.prudvi.trackbudget.model.TransactionStatus
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TrackDatabaseMigrationTest {
    @Test
    fun migratesV4ToLatest() = verifyMigration(fromVersion = 4, recurringColumn = true)

    @Test
    fun migratesV2ToLatest() = verifyMigration(fromVersion = 2, recurringColumn = false)

    @Test
    fun clearingTransactionsRemovesThem() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(target.cacheDir, "track-db-clear-${System.nanoTime()}").apply { mkdirs() }
        val database = TrackDatabase(TempDatabaseContext(target, dir))
        try {
            database.insert(Transaction("tx", 100_00, Direction.DEBIT, 1L, "Cafe", "food", status = TransactionStatus.CONFIRMED, source = TransactionSource.MANUAL))

            database.clear()

            assertTrue(database.transactions().isEmpty())
        } finally {
            database.close()
            dir.deleteRecursively()
        }
    }

    @Test
    fun findsATransactionByItsReference() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(target.cacheDir, "track-db-ref-${System.nanoTime()}").apply { mkdirs() }
        val database = TrackDatabase(TempDatabaseContext(target, dir))
        try {
            database.insert(Transaction("tx", 100_00, Direction.DEBIT, 1L, "Cafe", "food", status = TransactionStatus.CONFIRMED, source = TransactionSource.SMS, refId = "REF123456"))

            assertEquals("tx", database.findByRef("REF123456")?.id)
            assertEquals(null, database.findByRef("MISSING000"))
        } finally {
            database.close()
            dir.deleteRecursively()
        }
    }

    private fun verifyMigration(fromVersion: Int, recurringColumn: Boolean) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(target.cacheDir, "track-db-migration-$fromVersion-${System.nanoTime()}").apply { mkdirs() }
        val context = TempDatabaseContext(target, dir)
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("track_budget.db"), null).use { db ->
                createLegacyDatabase(db, recurringColumn)
                val columns = if (recurringColumn) ", recurring" else ""
                val values = if (recurringColumn) ", 0" else ""
                db.execSQL(
                    """
                    INSERT INTO transactions (
                        id, amount_minor, direction, occurred_at, merchant, category_id, note, status, source,
                        account_tail, sender, ref_id, source_key, refund_of_id, raw_message$columns
                    ) VALUES ('tx-1', 42000, 'DEBIT', 1776000000000, 'Blinkit', 'groceries', '', 'CONFIRMED', 'SMS', NULL, NULL, 'ref-1', 'source-1', NULL, NULL$values)
                    """.trimIndent(),
                )
                db.setVersion(fromVersion)
            }

            val helper = TrackDatabase(context)
            val transactions = helper.transactions()
            assertEquals(1, transactions.size)
            assertEquals("tx-1", transactions.single().id)
            assertEquals("Blinkit", transactions.single().merchant)
            assertEquals(42_000L, transactions.single().amountMinor)
            helper.readableDatabase.use { db ->
                assertEquals(6, db.version)
                assertEquals("ok", db.rawQuery("PRAGMA integrity_check", null).use { cursor -> cursor.moveToFirst(); cursor.getString(0) })
                listOf("stamps", "goals", "dismissed_drops", "period_snapshots").forEach { assertTrue(db.hasTable(it)) }
                assertTrue(db.hasIndex("tx_merchant"))
            }
            helper.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun createLegacyDatabase(db: SQLiteDatabase, recurringColumn: Boolean) {
        db.execSQL(
            """
            CREATE TABLE transactions (
                id TEXT PRIMARY KEY NOT NULL,
                amount_minor INTEGER NOT NULL CHECK(amount_minor >= 0),
                direction TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                merchant TEXT NOT NULL,
                category_id TEXT,
                note TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL,
                source TEXT NOT NULL,
                account_tail TEXT,
                sender TEXT,
                ref_id TEXT,
                source_key TEXT,
                refund_of_id TEXT,
                raw_message TEXT${if (recurringColumn) ", recurring INTEGER NOT NULL DEFAULT 0" else ""}
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX tx_occurred_at ON transactions(occurred_at)")
        db.execSQL("CREATE INDEX tx_status ON transactions(status)")
        db.execSQL("CREATE UNIQUE INDEX tx_ref ON transactions(ref_id) WHERE ref_id IS NOT NULL")
        db.execSQL("CREATE UNIQUE INDEX tx_source_key ON transactions(source_key) WHERE source_key IS NOT NULL")
    }

    private class TempDatabaseContext(base: Context, private val dir: File) : ContextWrapper(base) {
        override fun getDatabasePath(name: String): File = File(dir, name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
        override fun deleteDatabase(name: String): Boolean = getDatabasePath(name).delete()
    }
}

private fun SQLiteDatabase.hasTable(name: String): Boolean = rawQuery(
    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
    arrayOf(name),
).use { it.moveToFirst() }

private fun SQLiteDatabase.hasIndex(name: String): Boolean = rawQuery(
    "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
    arrayOf(name),
).use { it.moveToFirst() }
