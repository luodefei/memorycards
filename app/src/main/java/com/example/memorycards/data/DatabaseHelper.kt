package com.example.memorycards.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.memorycards.model.Card

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "memorycards.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_CARDS = "cards"
        private const val COL_ID = "id"
        private const val COL_FRONT = "front"
        private const val COL_BACK = "back"
        private const val COL_CATEGORY = "category"
        private const val COL_BOX = "box"
        private const val COL_NEXT_REVIEW = "next_review"
        private const val COL_CREATED_AT = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CARDS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FRONT TEXT NOT NULL,
                $COL_BACK TEXT NOT NULL,
                $COL_CATEGORY TEXT DEFAULT '',
                $COL_BOX INTEGER DEFAULT 1,
                $COL_NEXT_REVIEW INTEGER DEFAULT 0,
                $COL_CREATED_AT INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_CARDS ADD COLUMN $COL_CATEGORY TEXT DEFAULT ''")
        }
    }

    fun insertCard(card: Card): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FRONT, card.front)
            put(COL_BACK, card.back)
            put(COL_CATEGORY, card.category)
            put(COL_BOX, card.box)
            put(COL_NEXT_REVIEW, card.nextReview)
            put(COL_CREATED_AT, card.createdAt)
        }
        return db.insert(TABLE_CARDS, null, values)
    }

    // 批量导入：每项为 (正面, 背面, 分类)
    fun insertCards(cards: List<Triple<String, String, String>>): Int {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        var count = 0
        try {
            for ((front, back, category) in cards) {
                val values = ContentValues().apply {
                    put(COL_FRONT, front)
                    put(COL_BACK, back)
                    put(COL_CATEGORY, category)
                    put(COL_BOX, 1)
                    put(COL_NEXT_REVIEW, now)
                    put(COL_CREATED_AT, now)
                }
                db.insert(TABLE_CARDS, null, values)
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun getAllCards(categoryFilter: String = ""): List<Card> {
        val cards = mutableListOf<Card>()
        val db = readableDatabase
        val cursor = if (categoryFilter.isEmpty()) {
            db.rawQuery("SELECT * FROM $TABLE_CARDS ORDER BY $COL_CREATED_AT DESC", null)
        } else {
            db.rawQuery(
                "SELECT * FROM $TABLE_CARDS WHERE $COL_CATEGORY = ? ORDER BY $COL_CREATED_AT DESC",
                arrayOf(categoryFilter)
            )
        }
        while (cursor.moveToNext()) {
            cards.add(cardFromCursor(cursor))
        }
        cursor.close()
        return cards
    }

    fun getCardsForReview(categoryFilter: String = ""): List<Card> {
        val cards = mutableListOf<Card>()
        val db = readableDatabase
        val now = System.currentTimeMillis()
        val cursor = if (categoryFilter.isEmpty()) {
            db.rawQuery(
                "SELECT * FROM $TABLE_CARDS WHERE $COL_NEXT_REVIEW <= ? ORDER BY $COL_BOX ASC",
                arrayOf(now.toString())
            )
        } else {
            db.rawQuery(
                "SELECT * FROM $TABLE_CARDS WHERE $COL_NEXT_REVIEW <= ? AND $COL_CATEGORY = ? ORDER BY $COL_BOX ASC",
                arrayOf(now.toString(), categoryFilter)
            )
        }
        while (cursor.moveToNext()) {
            cards.add(cardFromCursor(cursor))
        }
        cursor.close()
        return cards
    }

    fun getAllCategories(): List<String> {
        val categories = mutableListOf<String>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT DISTINCT $COL_CATEGORY FROM $TABLE_CARDS WHERE $COL_CATEGORY != '' ORDER BY $COL_CATEGORY ASC",
            null
        )
        while (cursor.moveToNext()) {
            categories.add(cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)))
        }
        cursor.close()
        return categories
    }

    private fun cardFromCursor(cursor: android.database.Cursor): Card {
        return Card(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            front = cursor.getString(cursor.getColumnIndexOrThrow(COL_FRONT)),
            back = cursor.getString(cursor.getColumnIndexOrThrow(COL_BACK)),
            category = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)),
            box = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOX)),
            nextReview = cursor.getLong(cursor.getColumnIndexOrThrow(COL_NEXT_REVIEW)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
        )
    }

    fun updateCardReview(cardId: Long, remembered: Boolean) {
        val db = writableDatabase
        val currentBox = getCardBox(cardId)
        val newBox = if (remembered) {
            minOf(currentBox + 1, 5)
        } else {
            maxOf(1, currentBox - 1)
        }
        val intervals = mapOf(
            1 to 60_000L,
            2 to 600_000L,
            3 to 3_600_000L,
            4 to 86_400_000L,
            5 to 604_800_000L
        )
        val nextReview = System.currentTimeMillis() + (intervals[newBox] ?: 86_400_000L)
        val values = ContentValues().apply {
            put(COL_BOX, newBox)
            put(COL_NEXT_REVIEW, nextReview)
        }
        db.update(TABLE_CARDS, values, "$COL_ID = ?", arrayOf(cardId.toString()))
    }

    private fun getCardBox(cardId: Long): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_BOX FROM $TABLE_CARDS WHERE $COL_ID = ?",
            arrayOf(cardId.toString())
        )
        val box = if (cursor.moveToFirst()) {
            cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOX))
        } else {
            1
        }
        cursor.close()
        return box
    }

    fun updateCard(card: Card): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FRONT, card.front)
            put(COL_BACK, card.back)
            put(COL_CATEGORY, card.category)
        }
        return db.update(TABLE_CARDS, values, "$COL_ID = ?", arrayOf(card.id.toString()))
    }

    fun deleteCard(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_CARDS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getCardCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_CARDS", null)
        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return count
    }
}
