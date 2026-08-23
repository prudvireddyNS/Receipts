package com.prudvi.trackbudget.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionSource
import com.prudvi.trackbudget.model.TransactionStatus

class TrackDatabase(context: Context) : SQLiteOpenHelper(context, "track_budget.db", null, 4) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
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
                raw_message TEXT,
                recurring INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX tx_occurred_at ON transactions(occurred_at)")
        db.execSQL("CREATE INDEX tx_status ON transactions(status)")
        db.execSQL("CREATE UNIQUE INDEX tx_ref ON transactions(ref_id) WHERE ref_id IS NOT NULL")
        db.execSQL("CREATE UNIQUE INDEX tx_source_key ON transactions(source_key) WHERE source_key IS NOT NULL")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN source_key TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN refund_of_id TEXT")
            db.execSQL("CREATE UNIQUE INDEX tx_source_key ON transactions(source_key) WHERE source_key IS NOT NULL")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN recurring INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE transactions RENAME TO transactions_old")
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
                    raw_message TEXT,
                    recurring INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO transactions (
                    id, amount_minor, direction, occurred_at, merchant, category_id, note, status, source,
                    account_tail, sender, ref_id, source_key, refund_of_id, raw_message, recurring
                )
                SELECT id, amount_minor, direction, occurred_at, merchant, category_id, note, status, source,
                    account_tail, sender, ref_id, source_key, refund_of_id, raw_message, recurring
                FROM transactions_old
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE transactions_old")
            db.execSQL("CREATE INDEX tx_occurred_at ON transactions(occurred_at)")
            db.execSQL("CREATE INDEX tx_status ON transactions(status)")
            db.execSQL("CREATE UNIQUE INDEX tx_ref ON transactions(ref_id) WHERE ref_id IS NOT NULL")
            db.execSQL("CREATE UNIQUE INDEX tx_source_key ON transactions(source_key) WHERE source_key IS NOT NULL")
        }
    }

    @Synchronized
    fun transactions(): List<Transaction> = readableDatabase.query(
        "transactions",
        null,
        null,
        null,
        null,
        null,
        "occurred_at DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Transaction(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        amountMinor = cursor.getLong(cursor.getColumnIndexOrThrow("amount_minor")),
                        direction = Direction.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("direction"))),
                        occurredAt = cursor.getLong(cursor.getColumnIndexOrThrow("occurred_at")),
                        merchant = cursor.getString(cursor.getColumnIndexOrThrow("merchant")),
                        categoryId = cursor.stringOrNull("category_id"),
                        note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                        status = TransactionStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
                        source = TransactionSource.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("source"))),
                        accountTail = cursor.stringOrNull("account_tail"),
                        sender = cursor.stringOrNull("sender"),
                        refId = cursor.stringOrNull("ref_id"),
                        sourceKey = cursor.stringOrNull("source_key"),
                        refundOfId = cursor.stringOrNull("refund_of_id"),
                        rawMessage = cursor.stringOrNull("raw_message"),
                        recurring = cursor.getInt(cursor.getColumnIndexOrThrow("recurring")) == 1,
                    ),
                )
            }
        }
    }

    @Synchronized
    fun insert(transaction: Transaction): Boolean {
        val values = transaction.values()
        return writableDatabase.insertWithOnConflict("transactions", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    @Synchronized
    fun findByRef(refId: String): Transaction? = transactions().firstOrNull { it.refId == refId }

    @Synchronized
    fun update(transaction: Transaction) {
        writableDatabase.update("transactions", transaction.values(), "id = ?", arrayOf(transaction.id))
    }

    @Synchronized
    fun delete(id: String) {
        writableDatabase.delete("transactions", "id = ?", arrayOf(id))
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete("transactions", null, null)
    }

    private fun Transaction.values() = ContentValues().apply {
        put("id", id)
        put("amount_minor", amountMinor)
        put("direction", direction.name)
        put("occurred_at", occurredAt)
        put("merchant", merchant)
        put("category_id", categoryId)
        put("note", note)
        put("status", status.name)
        put("source", source.name)
        put("account_tail", accountTail)
        put("sender", sender)
        put("ref_id", refId)
        put("source_key", sourceKey)
        put("refund_of_id", refundOfId)
        put("raw_message", rawMessage)
        put("recurring", if (recurring) 1 else 0)
    }

    private fun android.database.Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }
}
