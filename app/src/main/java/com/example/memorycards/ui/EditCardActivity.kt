package com.example.memorycards.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.memorycards.data.DatabaseHelper
import com.example.memorycards.databinding.ActivityEditCardBinding
import com.example.memorycards.model.Card

class EditCardActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CARD_ID = "card_id"
        const val EXTRA_CARD_FRONT = "card_front"
        const val EXTRA_CARD_BACK = "card_back"
    }

    private lateinit var binding: ActivityEditCardBinding
    private lateinit var dbHelper: DatabaseHelper
    private var editingCardId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditCardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        // Check if editing existing card
        editingCardId = intent.getLongExtra(EXTRA_CARD_ID, -1)
        if (editingCardId != -1L) {
            binding.toolbar.title = "编辑卡片"
            binding.etFront.setText(intent.getStringExtra(EXTRA_CARD_FRONT))
            binding.etBack.setText(intent.getStringExtra(EXTRA_CARD_BACK))
        } else {
            binding.toolbar.title = "添加卡片"
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveCard()
        }
    }

    private fun saveCard() {
        val front = binding.etFront.text.toString().trim()
        val back = binding.etBack.text.toString().trim()

        if (front.isEmpty()) {
            binding.etFront.error = "请输入正面内容"
            return
        }
        if (back.isEmpty()) {
            binding.etBack.error = "请输入背面内容"
            return
        }

        if (editingCardId != -1L) {
            val card = Card(id = editingCardId, front = front, back = back)
            dbHelper.updateCard(card)
            Toast.makeText(this, "卡片已更新", Toast.LENGTH_SHORT).show()
        } else {
            val card = Card(front = front, back = back)
            dbHelper.insertCard(card)
            Toast.makeText(this, "卡片已添加", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
