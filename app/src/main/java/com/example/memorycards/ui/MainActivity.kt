package com.example.memorycards.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.memorycards.R
import com.example.memorycards.data.DatabaseHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: CardAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: android.widget.TextView
    private lateinit var btnStartReview: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnStartReview = findViewById(R.id.btnStartReview)

        setupRecyclerView()
        loadCards()

        findViewById<FloatingActionButton>(R.id.fabAddCard).setOnClickListener {
            startActivity(Intent(this, EditCardActivity::class.java))
        }

        btnStartReview.setOnClickListener {
            startActivity(Intent(this, ReviewActivity::class.java))
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

    private fun showBatchImportDialog() {
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 12
            maxLines = 20
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
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
        // Split by lines, then by first |
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        val cards = mutableListOf<Pair<String, String>>()

        for (line in lines) {
            val idx = line.indexOf('|')
            if (idx > 0) {
                val front = line.substring(0, idx).trim()
                val back = line.substring(idx + 1).trim()
                if (front.isNotBlank() && back.isNotBlank()) {
                    cards.add(Pair(front, back))
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
                })
            },
            onDeleteClick = { card ->
                AlertDialog.Builder(this)
                    .setTitle("删除")
                    .setMessage(getString(R.string.confirm_delete))
                    .setPositiveButton("确定") { _, _ ->
                        dbHelper.deleteCard(card.id)
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
        val cards = dbHelper.getAllCards()
        adapter.submitList(cards)

        if (cards.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            btnStartReview.isEnabled = false
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            btnStartReview.isEnabled = true
        }

        val reviewCards = dbHelper.getCardsForReview()
        btnStartReview.text = if (reviewCards.isEmpty()) {
            "暂无待复习卡片"
        } else {
            "开始复习 (${reviewCards.size})"
        }
    }

    override fun onResume() {
        super.onResume()
        loadCards()
    }
}
