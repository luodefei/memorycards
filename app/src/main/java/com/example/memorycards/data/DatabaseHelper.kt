package com.example.memorycards.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.memorycards.model.Card
import com.example.memorycards.model.Category

data class CategoryNode(
    val category: Category,
    val depth: Int,
    val cardCount: Int,
    val childCount: Int
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "memorycards.db"
        private const val DATABASE_VERSION = 3
        private const val TABLE_CARDS = "cards"
        private const val TABLE_CATEGORIES = "categories"
        private const val COL_ID = "id"
        private const val COL_FRONT = "front"
        private const val COL_BACK = "back"
        private const val COL_CATEGORY = "category"
        private const val COL_CATEGORY_ID = "category_id"
        private const val COL_BOX = "box"
        private const val COL_NEXT_REVIEW = "next_review"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_NAME = "name"
        private const val COL_PARENT_ID = "parent_id"
        private const val COL_SORT_ORDER = "sort_order"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_CATEGORIES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_PARENT_ID INTEGER,
                $COL_SORT_ORDER INTEGER DEFAULT 0,
                $COL_CREATED_AT INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_CARDS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FRONT TEXT NOT NULL,
                $COL_BACK TEXT NOT NULL,
                $COL_CATEGORY TEXT DEFAULT '',
                $COL_CATEGORY_ID INTEGER,
                $COL_BOX INTEGER DEFAULT 1,
                $COL_NEXT_REVIEW INTEGER DEFAULT 0,
                $COL_CREATED_AT INTEGER DEFAULT 0,
                FOREIGN KEY ($COL_CATEGORY_ID) REFERENCES $TABLE_CATEGORIES($COL_ID)
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_CARDS ADD COLUMN $COL_CATEGORY TEXT DEFAULT ''")
        }
        if (oldVersion < 3) {
            db.execSQL("""
                CREATE TABLE $TABLE_CATEGORIES (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_NAME TEXT NOT NULL,
                    $COL_PARENT_ID INTEGER,
                    $COL_SORT_ORDER INTEGER DEFAULT 0,
                    $COL_CREATED_AT INTEGER DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("ALTER TABLE $TABLE_CARDS ADD COLUMN $COL_CATEGORY_ID INTEGER")
            // migrate old category strings to tree
            migrateOldCategories(db)
        }
    }

    private fun migrateOldCategories(db: SQLiteDatabase) {
        val cursor = db.rawQuery(
            "SELECT DISTINCT $COL_CATEGORY FROM $TABLE_CARDS WHERE $COL_CATEGORY != ''", null
        )
        val catMap = mutableMapOf<String, Long>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY))
            // support path like "JVM/内存模型"
            val parts = name.split("/").map { it.trim() }.filter { it.isNotBlank() }
            var parentId: Long? = null
            for (part in parts) {
                val key = if (parentId != null) "$parentId:$part" else ":$part"
                val existing = catMap[key]
                if (existing != null) {
                    parentId = existing
                } else {
                    val values = ContentValues().apply {
                        put(COL_NAME, part)
                        if (parentId != null) put(COL_PARENT_ID, parentId)
                        else putNull(COL_PARENT_ID)
                        put(COL_SORT_ORDER, 0)
                        put(COL_CREATED_AT, System.currentTimeMillis())
                    }
                    val newId = db.insert(TABLE_CATEGORIES, null, values)
                    catMap[key] = newId
                    parentId = newId
                }
            }
            catMap[name] = parentId!!
        }
        cursor.close()

        // update cards
        for ((oldCat, newId) in catMap) {
            val values = ContentValues().apply { put(COL_CATEGORY_ID, newId) }
            db.update(TABLE_CARDS, values, "$COL_CATEGORY = ?", arrayOf(oldCat))
        }
    }

    // ── Cards CRUD ──

    fun insertCard(card: Card): Long {
        val db = writableDatabase
        val values = cardToValues(card)
        return db.insert(TABLE_CARDS, null, values)
    }

    fun insertCards(items: List<Triple<String, String, String?>>): Int {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        var count = 0
        try {
            for ((front, back, catPath) in items) {
                val catId = if (!catPath.isNullOrBlank()) {
                    ensureCategoryPath(catPath, db)
                } else null
                val values = ContentValues().apply {
                    put(COL_FRONT, front)
                    put(COL_BACK, back)
                    put(COL_CATEGORY, catPath ?: "")
                    if (catId != null) put(COL_CATEGORY_ID, catId)
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

    private fun ensureCategoryPath(path: String, db: SQLiteDatabase): Long? {
        val parts = path.split("/").map { it.trim() }.filter { it.isNotBlank() }
        var parentId: Long? = null
        for (part in parts) {
            parentId = findOrCreateCategory(part, parentId, db)
        }
        return parentId
    }

    private fun findOrCreateCategory(name: String, parentId: Long?, db: SQLiteDatabase): Long {
        val sel = if (parentId != null) "$COL_NAME = ? AND $COL_PARENT_ID = ?"
                  else "$COL_NAME = ? AND $COL_PARENT_ID IS NULL"
        val args = if (parentId != null) arrayOf(name, parentId.toString())
                   else arrayOf(name)
        val cursor = db.rawQuery(
            "SELECT $COL_ID FROM $TABLE_CATEGORIES WHERE $sel", args
        )
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(0)
            cursor.close()
            return id
        }
        cursor.close()
        val values = ContentValues().apply {
            put(COL_NAME, name)
        return db.insert(TABLE_CATEGORIES, null, values)
    }

    fun getAllCards(categoryId: Long? = null, includeChildren: Boolean = false): List<Card> {
        val db = readableDatabase
        val catIds = if (categoryId != null && includeChildren) {
            getSubtreeIds(categoryId, db)
        } else if (categoryId != null) {
            listOf(categoryId)
        } else null

        val cards = mutableListOf<Card>()
        val cursor = if (catIds != null) {
            val placeholders = catIds.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT * FROM $TABLE_CARDS WHERE $COL_CATEGORY_ID IN ($placeholders) ORDER BY $COL_CREATED_AT DESC",
                catIds.map { it.toString() }.toTypedArray()
            )
        } else {
            db.rawQuery("SELECT * FROM $TABLE_CARDS ORDER BY $COL_CREATED_AT DESC", null)
        }
        while (cursor.moveToNext()) {
            cards.add(cardFromCursor(cursor))
        }
        cursor.close()
        return cards
    }

    fun getCardsForReview(categoryId: Long? = null, includeChildren: Boolean = false): List<Card> {
        val db = readableDatabase
        val now = System.currentTimeMillis()
        val catIds = if (categoryId != null && includeChildren) {
            getSubtreeIds(categoryId, db)
        } else if (categoryId != null) {
            listOf(categoryId)
        } else null

        val cards = mutableListOf<Card>()
        val cursor = if (catIds != null) {
            val placeholders = catIds.joinToString(",") { "?" }
            val args = mutableListOf<String>(now.toString())
            args.addAll(catIds.map { it.toString() })
            db.rawQuery(
                "SELECT * FROM $TABLE_CARDS WHERE $COL_NEXT_REVIEW <= ? AND $COL_CATEGORY_ID IN ($placeholders) ORDER BY $COL_BOX ASC",
                args.toTypedArray()
            )
        } else {
            db.rawQuery(
                "SELECT * FROM $TABLE_CARDS WHERE $COL_NEXT_REVIEW <= ? ORDER BY $COL_BOX ASC",
                arrayOf(now.toString())
            )
        }
        while (cursor.moveToNext()) cards.add(cardFromCursor(cursor))
        cursor.close()
        return cards
    }

    private fun cardFromCursor(cursor: Cursor): Card {
        return Card(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            front = cursor.getString(cursor.getColumnIndexOrThrow(COL_FRONT)),
            back = cursor.getString(cursor.getColumnIndexOrThrow(COL_BACK)),
            categoryId = if (cursor.isNull(cursor.getColumnIndexOrThrow(COL_CATEGORY_ID))) null
                         else cursor.getLong(cursor.getColumnIndexOrThrow(COL_CATEGORY_ID)),
            categoryPath = cursor.getString(cursor.getColumnIndexOrThrow(COL_CATEGORY)) ?: "",
            box = cursor.getInt(cursor.getColumnIndexOrThrow(COL_BOX)),
            nextReview = cursor.getLong(cursor.getColumnIndexOrThrow(COL_NEXT_REVIEW)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
        )
    }

    private fun cardToValues(card: Card): ContentValues {
        return ContentValues().apply {
            put(COL_FRONT, card.front)
            put(COL_BACK, card.back)
            put(COL_CATEGORY, card.categoryPath)
            if (card.categoryId != null) put(COL_CATEGORY_ID, card.categoryId)
            else putNull(COL_CATEGORY_ID)
            put(COL_BOX, card.box)
            put(COL_NEXT_REVIEW, card.nextReview)
            put(COL_CREATED_AT, card.createdAt)
        }
    }

    fun updateCard(card: Card): Int {
        val db = writableDatabase
        return db.update(TABLE_CARDS, cardToValues(card), "$COL_ID = ?", arrayOf(card.id.toString()))
    }

    fun deleteCard(id: Long): Int {
        return writableDatabase.delete(TABLE_CARDS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun moveCards(cardIds: List<Long>, targetCategoryId: Long?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val path = if (targetCategoryId != null) getCategoryPath(targetCategoryId) else ""
            for (cardId in cardIds) {
                val values = ContentValues().apply {
                    put(COL_CATEGORY, path)
                    if (targetCategoryId != null) put(COL_CATEGORY_ID, targetCategoryId)
                    else putNull(COL_CATEGORY_ID)
                }
                db.update(TABLE_CARDS, values, "$COL_ID = ?", arrayOf(cardId.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getCardCount(): Int {
        val cursor = readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE_CARDS", null)
        val c = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        return c
    }

    fun updateCardReview(cardId: Long, remembered: Boolean) {
        val db = writableDatabase
        val currentBox = getCardBox(cardId)
        val newBox = if (remembered) minOf(currentBox + 1, 5) else maxOf(1, currentBox - 1)
        val intervals = mapOf(
            1 to 60_000L, 2 to 600_000L, 3 to 3_600_000L,
            4 to 86_400_000L, 5 to 604_800_000L
        )
        val nextReview = System.currentTimeMillis() + (intervals[newBox] ?: 86_400_000L)
        val values = ContentValues().apply {
            put(COL_BOX, newBox)
            put(COL_NEXT_REVIEW, nextReview)
        }
        db.update(TABLE_CARDS, values, "$COL_ID = ?", arrayOf(cardId.toString()))
    }

    private fun getCardBox(cardId: Long): Int {
        val cursor = readableDatabase.rawQuery(
            "SELECT $COL_BOX FROM $TABLE_CARDS WHERE $COL_ID = ?", arrayOf(cardId.toString())
        )
        val box = if (cursor.moveToFirst()) cursor.getInt(0) else 1
        cursor.close()
        return box
    }

    // ── Categories Tree ──

    fun getCategoryPath(categoryId: Long): String {
        val parts = mutableListOf<String>()
        var current: Long? = categoryId
        while (current != null) {
            val cursor = readableDatabase.rawQuery(
                "SELECT $COL_NAME, $COL_PARENT_ID FROM $TABLE_CATEGORIES WHERE $COL_ID = ?",
                arrayOf(current.toString())
            )
            if (cursor.moveToFirst()) {
                parts.add(0, cursor.getString(0))
                current = if (cursor.isNull(1)) null else cursor.getLong(1)
            } else break
            cursor.close()
        }
        return parts.joinToString("/")
    }

    fun getSubtreeIds(rootId: Long, db: SQLiteDatabase? = null): List<Long> {
        val database = db ?: readableDatabase
        val result = mutableListOf<Long>()
        val queue = ArrayDeque<Long>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val parentId = queue.removeFirst()
            result.add(parentId)
            val cursor = database.rawQuery(
                "SELECT $COL_ID FROM $TABLE_CATEGORIES WHERE $COL_PARENT_ID = ?",
                arrayOf(parentId.toString())
            )
            while (cursor.moveToNext()) queue.add(cursor.getLong(0))
            cursor.close()
        }
        return result
    }

    fun getCategoryNodes(): List<CategoryNode> {
        val db = readableDatabase
        val allCats = mutableListOf<Category>()
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_CATEGORIES ORDER BY $COL_SORT_ORDER, $COL_NAME", null
        )
        while (cursor.moveToNext()) {
            allCats.add(Category(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                parentId = if (cursor.isNull(cursor.getColumnIndexOrThrow(COL_PARENT_ID))) null
                           else cursor.getLong(cursor.getColumnIndexOrThrow(COL_PARENT_ID)),
                sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SORT_ORDER)),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
            ))
        }
        cursor.close()

        // count cards per category
        val cardCounts = mutableMapOf<Long?, Int>()
        val cc = db.rawQuery("SELECT $COL_CATEGORY_ID FROM $TABLE_CARDS", null)
        while (cc.moveToNext()) {
            val cid = if (cc.isNull(0)) null else cc.getLong(0)
            cardCounts[cid] = (cardCounts[cid] ?: 0) + 1
        }
        cc.close()

        // count children
        val childCounts = mutableMapOf<Long, Int>()
        for (cat in allCats) {
            if (cat.parentId != null) {
                childCounts[cat.parentId] = (childCounts[cat.parentId] ?: 0) + 1
            }
        }

        // flatten tree
        val result = mutableListOf<CategoryNode>()
        val roots = allCats.filter { it.parentId == null }
        for (root in roots) {
            addNodeWithChildren(root, 0, allCats, cardCounts, childCounts, result)
        }
        return result
    }

    private fun addNodeWithChildren(
        cat: Category, depth: Int,
        allCats: List<Category>,
        cardCounts: Map<Long?, Int>,
        childCounts: Map<Long, Int>,
        result: MutableList<CategoryNode>
    ) {
        result.add(CategoryNode(
            category = cat,
            depth = depth,
            cardCount = cardCounts[cat.id] ?: 0,
            childCount = childCounts[cat.id] ?: 0
        ))
        val children = allCats.filter { it.parentId == cat.id }
        for (child in children) {
            addNodeWithChildren(child, depth + 1, allCats, cardCounts, childCounts, result)
        }
    }

    fun createCategory(name: String, parentId: Long?): Long {
        val values = ContentValues().apply {
            put(COL_NAME, name)
        return writableDatabase.insert(TABLE_CATEGORIES, null, values)
    }

    fun deleteCategory(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // move children up to parent
            val cursor = db.rawQuery("SELECT $COL_PARENT_ID FROM $TABLE_CATEGORIES WHERE $COL_ID = ?", arrayOf(id.toString()))
            val parentId = if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            cursor.close()

            val childValues = ContentValues().apply {
            db.update(TABLE_CATEGORIES, childValues, "$COL_PARENT_ID = ?", arrayOf(id.toString()))

            // uncategorize cards in this node
            val cardValues = ContentValues().apply { putNull(COL_CATEGORY_ID) }
            db.update(TABLE_CARDS, cardValues, "$COL_CATEGORY_ID = ?", arrayOf(id.toString()))

            // delete the node
            db.delete(TABLE_CATEGORIES, "$COL_ID = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun renameCategory(id: Long, newName: String) {
        val values = ContentValues().apply { put(COL_NAME, newName) }
        writableDatabase.update(TABLE_CATEGORIES, values, "$COL_ID = ?", arrayOf(id.toString()))
        // also update category_path in cards (lazy, will refresh on next query)
    }

    fun getCategory(categoryId: Long): Category? {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM $TABLE_CATEGORIES WHERE $COL_ID = ?", arrayOf(categoryId.toString())
        )
        return if (cursor.moveToFirst()) {
            val cat = Category(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                parentId = if (cursor.isNull(cursor.getColumnIndexOrThrow(COL_PARENT_ID))) null
                           else cursor.getLong(cursor.getColumnIndexOrThrow(COL_PARENT_ID)),
                sortOrder = cursor.getInt(cursor.getColumnIndexOrThrow(COL_SORT_ORDER)),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))
            )
            cursor.close()
            cat
        } else {
            cursor.close()
            null
        }
    }
}
