package com.prudvi.trackbudget.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.prudvi.trackbudget.model.Direction
import com.prudvi.trackbudget.model.DismissedDropRule
import com.prudvi.trackbudget.model.EarnedStamp
import com.prudvi.trackbudget.model.Goal
import com.prudvi.trackbudget.model.PeriodSnapshot
import com.prudvi.trackbudget.model.Transaction
import com.prudvi.trackbudget.model.TransactionSource
import com.prudvi.trackbudget.model.TransactionStatus

class TrackDatabase(context: Context) : SQLiteOpenHelper(context, "track_budget.db", null, 6) {
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
                recurring INTEGER NOT NULL DEFAULT 0,
                committed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX tx_occurred_at ON transactions(occurred_at)")
        db.execSQL("CREATE INDEX tx_status ON transactions(status)")
        db.execSQL("CREATE UNIQUE INDEX tx_ref ON transactions(ref_id) WHERE ref_id IS NOT NULL")
        db.execSQL("CREATE UNIQUE INDEX tx_source_key ON transactions(source_key) WHERE source_key IS NOT NULL")
        createReceiptsTables(db)
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
        if (oldVersion < 5) {
            createReceiptsTables(db)
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN committed INTEGER NOT NULL DEFAULT 0")
        }
    }

    private fun createReceiptsTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE stamps (
                id TEXT PRIMARY KEY NOT NULL,
                earned_at INTEGER NOT NULL,
                period_key TEXT,
                seen INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE goals (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                target_minor INTEGER NOT NULL CHECK(target_minor > 0),
                saved_minor INTEGER NOT NULL DEFAULT 0,
                target_epoch_day INTEGER,
                created_at INTEGER NOT NULL,
                completed_at INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE dismissed_drops (
                rule_key TEXT PRIMARY KEY NOT NULL,
                dismissed_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE period_snapshots (
                period_key TEXT PRIMARY KEY NOT NULL,
                budget_minor INTEGER NOT NULL,
                spent_minor INTEGER NOT NULL,
                closed_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX tx_merchant ON transactions(merchant)")
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
                        committed = cursor.getInt(cursor.getColumnIndexOrThrow("committed")) == 1,
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
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("transactions", null, null)
            db.delete("period_snapshots", null, null)
            db.delete("dismissed_drops", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun goals(): List<Goal> = readableDatabase.query("goals", null, null, null, null, null, "created_at ASC").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Goal(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        targetMinor = cursor.getLong(cursor.getColumnIndexOrThrow("target_minor")),
                        savedMinor = cursor.getLong(cursor.getColumnIndexOrThrow("saved_minor")),
                        targetEpochDay = cursor.longOrNull("target_epoch_day"),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                        completedAt = cursor.longOrNull("completed_at"),
                    ),
                )
            }
        }
    }

    @Synchronized
    fun upsertGoal(goal: Goal) {
        writableDatabase.insertWithOnConflict("goals", null, goal.values(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun deleteGoal(id: String) {
        writableDatabase.delete("goals", "id = ?", arrayOf(id))
    }

    @Synchronized
    fun earnedStamps(): List<EarnedStamp> = readableDatabase.query("stamps", null, null, null, null, null, "earned_at ASC").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    EarnedStamp(
                        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                        earnedAt = cursor.getLong(cursor.getColumnIndexOrThrow("earned_at")),
                        periodKey = cursor.stringOrNull("period_key"),
                        seen = cursor.getInt(cursor.getColumnIndexOrThrow("seen")) == 1,
                    ),
                )
            }
        }
    }

    @Synchronized
    fun insertEarnedStamp(stamp: EarnedStamp): Boolean =
        writableDatabase.insertWithOnConflict("stamps", null, stamp.values(), SQLiteDatabase.CONFLICT_IGNORE) != -1L

    @Synchronized
    fun markStampSeen(id: String) {
        writableDatabase.update("stamps", ContentValues().apply { put("seen", 1) }, "id = ?", arrayOf(id))
    }

    @Synchronized
    fun dismissedDropRules(): List<DismissedDropRule> = readableDatabase.query("dismissed_drops", null, null, null, null, null, "dismissed_at DESC").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    DismissedDropRule(
                        ruleKey = cursor.getString(cursor.getColumnIndexOrThrow("rule_key")),
                        dismissedAt = cursor.getLong(cursor.getColumnIndexOrThrow("dismissed_at")),
                    ),
                )
            }
        }
    }

    @Synchronized
    fun upsertDismissedDropRule(rule: DismissedDropRule) {
        writableDatabase.insertWithOnConflict("dismissed_drops", null, rule.values(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun deleteDismissedDropRule(ruleKey: String) {
        writableDatabase.delete("dismissed_drops", "rule_key = ?", arrayOf(ruleKey))
    }

    @Synchronized
    fun periodSnapshots(): List<PeriodSnapshot> = readableDatabase.query("period_snapshots", null, null, null, null, null, "period_key ASC").use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    PeriodSnapshot(
                        periodKey = cursor.getString(cursor.getColumnIndexOrThrow("period_key")),
                        budgetMinor = cursor.getLong(cursor.getColumnIndexOrThrow("budget_minor")),
                        spentMinor = cursor.getLong(cursor.getColumnIndexOrThrow("spent_minor")),
                        closedAt = cursor.getLong(cursor.getColumnIndexOrThrow("closed_at")),
                    ),
                )
            }
        }
    }

    @Synchronized
    fun insertPeriodSnapshot(snapshot: PeriodSnapshot): Boolean =
        writableDatabase.insertWithOnConflict("period_snapshots", null, snapshot.values(), SQLiteDatabase.CONFLICT_IGNORE) != -1L

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
        put("committed", if (committed) 1 else 0)
    }

    private fun Goal.values() = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("target_minor", targetMinor)
        put("saved_minor", savedMinor)
        if (targetEpochDay == null) putNull("target_epoch_day") else put("target_epoch_day", targetEpochDay)
        put("created_at", createdAt)
        if (completedAt == null) putNull("completed_at") else put("completed_at", completedAt)
    }

    private fun EarnedStamp.values() = ContentValues().apply {
        put("id", id)
        put("earned_at", earnedAt)
        put("period_key", periodKey)
        put("seen", if (seen) 1 else 0)
    }

    private fun DismissedDropRule.values() = ContentValues().apply {
        put("rule_key", ruleKey)
        put("dismissed_at", dismissedAt)
    }

    private fun PeriodSnapshot.values() = ContentValues().apply {
        put("period_key", periodKey)
        put("budget_minor", budgetMinor)
        put("spent_minor", spentMinor)
        put("closed_at", closedAt)
    }

    private fun android.database.Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun android.database.Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }
}
