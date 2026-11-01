package com.example.memorycards.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.memorycards.R
import com.example.memorycards.data.DatabaseHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: CardAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnStartReview: android.widget.Button
    private lateinit var categoryTabs: LinearLayout
    private lateinit var scrollTabs: HorizontalScrollView

    private var categories: List<String> = emptyList()
    private var selectedCategory: String = "" // empty = all

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnStartReview = findViewById(R.id.btnStartReview)
        categoryTabs = findViewById(R.id.categoryTabs)
        scrollTabs = findViewById(R.id.scrollTabs)

        setupRecyclerView()
        loadCategories()
        loadCards()

        findViewById<FloatingActionButton>(R.id.fabAddCard).setOnClickListener {
            startActivity(Intent(this, EditCardActivity::class.java))
        }

        btnStartReview.setOnClickListener {
            val intent = Intent(this, ReviewActivity::class.java)
            if (selectedCategory.isNotEmpty()) {
                intent.putExtra(ReviewActivity.EXTRA_CATEGORY, selectedCategory)
            }
            startActivity(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_batch_import -> {
                showBatchImportDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadCategories() {
        categories = dbHelper.getAllCategories()
        categoryTabs.removeAllViews()

        // "全部" tab
        addCategoryTab("全部", selectedCategory.isEmpty())
        for (cat in categories) {
            addCategoryTab(cat, cat == selectedCategory)
        }

        // show/hide scroll
        scrollTabs.visibility = if (categories.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun addCategoryTab(label: String, isSelected: Boolean) {
        val chip = TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(20, 10, 20, 10)
            if (isSelected) {
                setTextColor(ContextCompat.getColor(context, R.color.white))
                background = ContextCompat.getDrawable(context, R.drawable.chip_selected_bg)
                typeface = Typeface.DEFAULT_BOLD
            } else {
                setTextColor(ContextCompat.getColor(context, R.color.purple_700))
                background = ContextCompat.getDrawable(context, R.drawable.chip_unselected_bg)
                typeface = Typeface.DEFAULT
            }
            setOnClickListener {
                val clickedCat = if (label == "全部") "" else label
                if (clickedCat != selectedCategory) {
                    selectedCategory = clickedCat
                    loadCategories()
                    loadCards()
                }
            }
        }
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = 12
        }
        categoryTabs.addView(chip, params)
    }

    private fun showBatchImportDialog() {
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 12
            maxLines = 20
            gravity = Gravity.TOP or Gravity.START
            setPadding(32, 32, 32, 32)
            textSize = 14f
            hint = getString(R.string.batch_import_hint)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 0)
            addView(editText)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.batch_import))
            .setMessage(getString(R.string.batch_import_desc))
            .setView(container)
            .setPositiveButton(getString(R.string.import_btn)) { dialog, _ ->
                val text = editText.text.toString().trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, R.string.import_empty, Toast.LENGTH_SHORT).show()
                } else {
                    val count = parseAndImport(text)
                    if (count > 0) {
                        Toast.makeText(this, getString(R.string.import_success, count), Toast.LENGTH_SHORT).show()
                        loadCategories()
                        loadCards()
                    } else {
                        Toast.makeText(this, R.string.import_fail, Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun parseAndImport(text: String): Int {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val cards = mutableListOf<Triple<String, String, String>>()

        for (line in lines) {
            val parts = line.split("|").map { it.trim() }
            when (parts.size) {
                2 -> {
                    val front = parts[0]
                    val back = parts[1]
                    if (front.isNotBlank() && back.isNotBlank()) {
                        cards.add(Triple(front, back, ""))
                    }
                }
                3 -> {
                    val front = parts[0]
                    val back = parts[1]
                    val category = parts[2]
                    if (front.isNotBlank() && back.isNotBlank()) {
                        cards.add(Triple(front, back, category))
                    }
                }
            }
        }

        if (cards.isEmpty()) return 0
        return dbHelper.insertCards(cards)
    }

    private fun setupRecyclerView() {
        adapter = CardAdapter(
            onEditClick = { card ->
                startActivity(Intent(this, EditCardActivity::class.java).apply {
                    putExtra(EditCardActivity.EXTRA_CARD_ID, card.id)
                    putExtra(EditCardActivity.EXTRA_CARD_FRONT, card.front)
                    putExtra(EditCardActivity.EXTRA_CARD_BACK, card.back)
                    putExtra(EditCardActivity.EXTRA_CARD_CATEGORY, card.category)
                })
            },
            onDeleteClick = { card ->
                AlertDialog.Builder(this)
                    .setTitle("删除")
                    .setMessage(getString(R.string.confirm_delete))
                    .setPositiveButton("确定") { _, _ ->
                        dbHelper.deleteCard(card.id)
                        loadCategories()
                        loadCards()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadCards() {
        val cards = dbHelper.getAllCards(categoryFilter = selectedCategory)
        adapter.submitList(cards)

        if (cards.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (selectedCategory.isEmpty()) getString(R.string.no_cards)
                else "「$selectedCategory」分类下暂无卡片"
            recyclerView.visibility = View.GONE
            btnStartReview.isEnabled = false
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            btnStartReview.isEnabled = true
        }

        val reviewCards = dbHelper.getCardsForReview(categoryFilter = selectedCategory)
        btnStartReview.text = if (reviewCards.isEmpty()) {
            "暂无待复习卡片"
        } else {
            "开始复习 (${reviewCards.size})"
        }
    }

    override fun onResume() {
        super.onResume()
        loadCategories()
        loadCards()
    }
}
