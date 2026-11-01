package com.example.memorycards.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.memorycards.model.Card

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "memorycards.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_CARDS = "cards"
        private const val COL_ID = "id"
        private const val COL_FRONT = "front"
        private const val COL_BACK = "back"
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
                $COL_BOX INTEGER DEFAULT 1,
                $COL_NEXT_REVIEW INTEGER DEFAULT 0,
                $COL_CREATED_AT INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CARDS")
        onCreate(db)
    }

    fun insertCard(card: Card): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FRONT, card.front)
            put(COL_BACK, card.back)
            put(COL_BOX, card.box)
            put(COL_NEXT_REVIEW, card.nextReview)
            put(COL_CREATED_AT, card.createdAt)
        }
        return db.insert(TABLE_CARDS, null, values)
    }

    fun getAllCards(): List<Card> {
        val cards = mutableListOf<Card>()
        val db = readableDatabase
        val cursor = db.query(TABLE_CARDS, null, null, null, null, null, "$COL_CREATED_AT DESC")
        while (cursor.moveToNext()) {
            cards.add(
                Card(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                    front = cursor.getString(cursor.getColumnIndexOrThrow(COL_FRONT)),
                    back = cursor.getString(cursor.getColumnIndexOrThrow(COL_BACK)),
                    box = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOX)),
                    nextReview = cursor.getLong(cursor.getColumnIndexOrThrow(COL_NEXT_REVIEW)),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
                )
            )
        }
        cursor.close()
        return cards
    }

    fun getCardsForReview(): List<Card> {
        val cards = mutableListOf<Card>()
        val db = readableDatabase
        val now = System.currentTimeMillis()
        val cursor = db.query(
            TABLE_CARDS, null,
            "$COL_NEXT_REVIEW <= ?",
            arrayOf(now.toString()),
            null, null, "$COL_BOX ASC"
        )
        while (cursor.moveToNext()) {
            cards.add(
                Card(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                    front = cursor.getString(cursor.getColumnIndexOrThrow(COL_FRONT)),
                    back = cursor.getString(cursor.getColumnIndexOrThrow(COL_BACK)),
                    box = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOX)),
                    nextReview = cursor.getLong(cursor.getColumnIndexOrThrow(COL_NEXT_REVIEW)),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
                )
            )
        }
        cursor.close()
        return cards
    }

    fun updateCardReview(cardId: Long, remembered: Boolean) {
        val db = writableDatabase
        val currentBox = getCardBox(cardId)
        val newBox = if (remembered) {
            minOf(currentBox + 1, 5)
        } else {
            maxOf(1, currentBox - 1)
        }
        // Leitner intervals in milliseconds (approximate)
        val intervals = mapOf(
            1 to 60_000L,           // 1 minute
            2 to 600_000L,          // 10 minutes
            3 to 3_600_000L,        // 1 hour
            4 to 86_400_000L,       // 1 day
            5 to 604_800_000L       // 1 week
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
        val cursor = db.query(
            TABLE_CARDS,
            arrayOf(COL_BOX),
            "$COL_ID = ?",
            arrayOf(cardId.toString()),
            null, null, null
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
