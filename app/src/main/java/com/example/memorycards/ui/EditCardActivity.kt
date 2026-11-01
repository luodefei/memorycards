package com.example.memorycards.ui

import android.os.Bundle
import android.widget.ArrayAdapter
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
        const val EXTRA_CATEGORY_ID = "category_id"
    }

    private lateinit var binding: ActivityEditCardBinding
    private lateinit var dbHelper: DatabaseHelper
    private var editingCardId: Long = -1
    private var selectedCategoryId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditCardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        editingCardId = intent.getLongExtra(EXTRA_CARD_ID, -1)
        val initialCatId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1)
        if (initialCatId > 0) selectedCategoryId = initialCatId

        if (editingCardId != -1L) {
            binding.toolbar.title = "编辑卡片"
            binding.etFront.setText(intent.getStringExtra(EXTRA_CARD_FRONT))
            binding.etBack.setText(intent.getStringExtra(EXTRA_CARD_BACK))
            val catId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1)
            if (catId > 0) {
                selectedCategoryId = catId
                binding.tvSelectedCategory.text = dbHelper.getCategoryPath(catId)
            }
        } else {
            binding.toolbar.title = "添加卡片"
            if (selectedCategoryId != null) {
                binding.tvSelectedCategory.text = dbHelper.getCategoryPath(selectedCategoryId!!)
            }
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnPickCategory.setOnClickListener { showCategoryPicker() }
        binding.btnClearCategory.setOnClickListener {
            selectedCategoryId = null
            binding.tvSelectedCategory.text = "未分类"
        }
        binding.btnSave.setOnClickListener { saveCard() }
    }

    private fun showCategoryPicker() {
        val nodes = dbHelper.getCategoryNodes()
        val builder = android.app.AlertDialog.Builder(this)
        val scrollView = android.widget.ScrollView(this)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        fun addOption(label: String, catId: Long?) {
            val tv = android.widget.TextView(this).apply {
                text = label
                textSize = 15f
                setPadding(16, 14, 16, 14)
                if (catId == selectedCategoryId) {
                    setTextColor(getColor(R.color.purple_500))
                }
                setOnClickListener {
                    selectedCategoryId = catId
                    binding.tvSelectedCategory.text = if (catId != null) dbHelper.getCategoryPath(catId) else "未分类"
                    (it.parent.parent.parent as? android.app.AlertDialog)?.dismiss()
                }
            }
            container.addView(tv)
        }

        addOption("📄 未分类", null)
        for (node in nodes) {
            val indent = "    ".repeat(node.depth)
            addOption("$indent📂 ${node.category.name}", node.category.id)
        }

        scrollView.addView(container)
        builder
            .setTitle("选择分类")
            .setView(scrollView)
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveCard() {
        val front = binding.etFront.text.toString().trim()
        val back = binding.etBack.text.toString().trim()

        if (front.isEmpty()) { binding.etFront.error = "请输入正面内容"; return }
        if (back.isEmpty()) { binding.etBack.error = "请输入背面内容"; return }

        if (editingCardId != -1L) {
            val card = Card(
                id = editingCardId, front = front, back = back,
                categoryId = selectedCategoryId,
                categoryPath = if (selectedCategoryId != null) dbHelper.getCategoryPath(selectedCategoryId!!) else ""
            )
            dbHelper.updateCard(card)
            Toast.makeText(this, "卡片已更新", Toast.LENGTH_SHORT).show()
        } else {
            val card = Card(
                front = front, back = back,
                categoryId = selectedCategoryId,
                categoryPath = if (selectedCategoryId != null) dbHelper.getCategoryPath(selectedCategoryId!!) else ""
            )
            dbHelper.insertCard(card)
            Toast.makeText(this, "卡片已添加", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
