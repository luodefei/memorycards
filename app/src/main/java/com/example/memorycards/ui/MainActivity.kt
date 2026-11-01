package com.example.memorycards.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.memorycards.data.DatabaseHelper
import com.example.memorycards.databinding.ActivityMainBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: CardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        setupRecyclerView()
        loadCards()

        binding.fabAddCard.setOnClickListener {
            val intent = Intent(this, EditCardActivity::class.java)
            startActivity(intent)
        }

        binding.btnStartReview.setOnClickListener {
            val intent = Intent(this, ReviewActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        adapter = CardAdapter(
            onEditClick = { card ->
                val intent = Intent(this, EditCardActivity::class.java).apply {
                    putExtra(EditCardActivity.EXTRA_CARD_ID, card.id)
                    putExtra(EditCardActivity.EXTRA_CARD_FRONT, card.front)
                    putExtra(EditCardActivity.EXTRA_CARD_BACK, card.back)
                }
                startActivity(intent)
            },
            onDeleteClick = { card ->
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("删除")
                    .setMessage(getString(com.example.memorycards.R.string.confirm_delete))
                    .setPositiveButton("确定") { _, _ ->
                        dbHelper.deleteCard(card.id)
                        loadCards()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadCards() {
        val cards = dbHelper.getAllCards()
        adapter.submitList(cards)

        if (cards.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.btnStartReview.isEnabled = false
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            binding.btnStartReview.isEnabled = true
        }

        // Update review button text with count
        val reviewCards = dbHelper.getCardsForReview()
        binding.btnStartReview.text = if (reviewCards.isEmpty()) {
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
