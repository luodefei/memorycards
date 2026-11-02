package com.example.memorycards.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.memorycards.R
import com.example.memorycards.data.CategoryNode
import com.example.memorycards.data.DatabaseHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: CardAdapter
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnStartReview: Button
    private lateinit var treeContainer: LinearLayout
    private lateinit var tvCurrentPath: TextView
    private lateinit var toolbar: Toolbar

    private var currentNodeId: Long? = null
    private var includeChildren = false

    // multi-select mode
    private var selectionMode = false
    private val selectedCardIds = mutableSetOf<Long>()
    private lateinit var selectionBar: LinearLayout
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnSelectAll: Button
    private lateinit var btnMoveSelected: Button
    private lateinit var btnCancelSelect: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)

        drawerLayout = findViewById(R.id.drawerLayout)
        treeContainer = findViewById(R.id.treeContainer)
        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnStartReview = findViewById(R.id.btnStartReview)
        selectionBar = findViewById(R.id.selectionBar)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        btnMoveSelected = findViewById(R.id.btnMoveSelected)
        btnCancelSelect = findViewById(R.id.btnCancelSelect)

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
        }

        // toggle include children mode
        findViewById<View>(R.id.btnToggleChildren).setOnClickListener {
            includeChildren = !includeChildren
            it.isActivated = includeChildren
            loadCards()
        }

        findViewById<View>(R.id.btnBatchImport).setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showBatchImportDialog()
        }

        findViewById<View>(R.id.ivAvatar).setOnClickListener {
            Toast.makeText(this, "登录 / 会员中心即将上线", Toast.LENGTH_SHORT).show()
        }

        setupRecyclerView()
        loadTree()
        loadCards()

        findViewById<TextView>(R.id.btnAddRootCategory).setOnClickListener { showAddRootCategoryDialog() }

        findViewById<FloatingActionButton>(R.id.fabAddCard).setOnClickListener {
            val intent = Intent(this, EditCardActivity::class.java)
            if (currentNodeId != null) intent.putExtra(EditCardActivity.EXTRA_CATEGORY_ID, currentNodeId)
            startActivity(intent)
        }

        btnStartReview.setOnClickListener {
            val intent = Intent(this, ReviewActivity::class.java)
            if (currentNodeId != null) {
                intent.putExtra(ReviewActivity.EXTRA_CATEGORY_ID, currentNodeId)
                intent.putExtra(ReviewActivity.EXTRA_INCLUDE_CHILDREN, includeChildren)
            }
            startActivity(intent)
        }

        // Selection bar
        btnCancelSelect.setOnClickListener { exitSelectionMode() }
        btnSelectAll.setOnClickListener {
            val allIds = (adapter.currentList).map { it.id }.toSet()
            if (selectedCardIds.containsAll(allIds)) {
                selectedCardIds.clear()
            } else {
                selectedCardIds.addAll(allIds)
            }
            updateSelectionUI()
            adapter.notifyDataSetChanged()
        }
        btnMoveSelected.setOnClickListener {
            showMoveTreeDialog()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START))
                    drawerLayout.closeDrawer(GravityCompat.START)
                else
                    drawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (selectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadTree() {
        val nodes = dbHelper.getCategoryNodes()
        treeContainer.removeAllViews()

        // all cards root
        addTreeItem(treeContainer, "📚 全部卡片", null, 0, dbHelper.getCardCount(), nodes.count { it.depth == 1 }, currentNodeId == null && !includeChildren)

        // uncategorized
        addTreeItem(treeContainer, "📄 未分类", -1L, 0, 0, 0, currentNodeId == -1L)

        for (node in nodes) {
            addTreeItem(treeContainer, node, currentNodeId == node.category.id && !includeChildren)
        }
    }

    private fun addTreeItem(parent: ViewGroup, label: String, categoryId: Long?, depth: Int, cardCount: Int, childCount: Int, selected: Boolean) {
        val item = layoutInflater.inflate(R.layout.item_tree_node, parent, false)
        val tvName = item.findViewById<TextView>(R.id.tvNodeName)
        val tvCount = item.findViewById<TextView>(R.id.tvNodeCount)
        val indent = item.findViewById<View>(R.id.indent)

        indent.layoutParams = (indent.layoutParams as LinearLayout.LayoutParams).apply {
            width = (depth * 24).dpToPx()
        }
        tvName.text = label
        if (categoryId == null) {
            tvCount.text = "$cardCount"
        } else if (categoryId == -1L) {
            tvCount.text = ""
        } else {
            tvCount.text = if (childCount > 0 && cardCount > 0) "$cardCount📁"
            else if (childCount > 0) "📁" else "$cardCount"
        }

        if (selected) {
            item.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_node_bg))
            tvName.typeface = Typeface.DEFAULT_BOLD
        }

        item.setOnClickListener {
            currentNodeId = if (categoryId == -1L) null else categoryId
            includeChildren = false
            findViewById<View>(R.id.btnToggleChildren).isActivated = false
            updatePathDisplay()
            loadTree()
            loadCards()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        item.setOnLongClickListener {
            if (categoryId != null && categoryId > 0) {
                showCategoryManageDialog(categoryId)
            }
            true
        }

        parent.addView(item)
    }

    private fun addTreeItem(parent: ViewGroup, node: CategoryNode, selected: Boolean) {
        val item = layoutInflater.inflate(R.layout.item_tree_node, parent, false)
        val tvName = item.findViewById<TextView>(R.id.tvNodeName)
        val tvCount = item.findViewById<TextView>(R.id.tvNodeCount)
        val indent = item.findViewById<View>(R.id.indent)

        indent.layoutParams = (indent.layoutParams as LinearLayout.LayoutParams).apply {
            width = (node.depth * 24).dpToPx()
        }

        val expandIcon = if (node.childCount > 0) "▸ " else "  "
        tvName.text = expandIcon + node.category.name
        val info = mutableListOf<String>()
        if (node.cardCount > 0) info.add("${node.cardCount}")
        if (node.childCount > 0) info.add("📁")
        tvCount.text = info.joinToString(" ")

        if (selected) {
            item.setBackgroundColor(ContextCompat.getColor(this, R.color.selected_node_bg))
            tvName.typeface = Typeface.DEFAULT_BOLD
        }

        item.setOnClickListener {
            currentNodeId = node.category.id
            includeChildren = false
            findViewById<View>(R.id.btnToggleChildren).isActivated = false
            updatePathDisplay()
            loadTree()
            loadCards()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        item.setOnLongClickListener {
            showCategoryManageDialog(node.category.id)
            true
        }

        parent.addView(item)
    }

    private fun updatePathDisplay() {
        tvCurrentPath.text = if (currentNodeId != null) {
            val cat = dbHelper.getCategory(currentNodeId!!)
            if (cat != null) "📂 ${dbHelper.getCategoryPath(currentNodeId!!)}" else "📚 全部卡片"
        } else "📚 全部卡片"
    }

    private fun showCategoryManageDialog(categoryId: Long) {
        val items = arrayOf("重命名", "添加子节点", "删除")
        val cat = dbHelper.getCategory(categoryId) ?: return
        AlertDialog.Builder(this)
            .setTitle(cat.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showRenameDialog(categoryId, cat.name)
                    1 -> showAddChildDialog(categoryId)
                    2 -> confirmDeleteCategory(categoryId, cat.name)
                }
            }
            .show()
    }

    private fun showRenameDialog(categoryId: Long, oldName: String) {
        val editText = EditText(this).apply {
            setText(oldName)
            setPadding(40, 20, 40, 20)
            textSize = 16f
        }
        AlertDialog.Builder(this)
            .setTitle("重命名")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                dbHelper.renameCategory(categoryId, editText.text.toString().trim())
                loadTree()
                updatePathDisplay()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddChildDialog(parentId: Long) {
        val editText = EditText(this).apply {
            hint = "子节点名称"
            setPadding(40, 20, 40, 20)
            textSize = 16f
        }
        AlertDialog.Builder(this)
            .setTitle("添加子节点")
            .setView(editText)
            .setPositiveButton("添加") { _, _ ->
                dbHelper.createCategory(editText.text.toString().trim(), parentId)
                loadTree()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteCategory(categoryId: Long, name: String) {
        AlertDialog.Builder(this)
            .setTitle("删除 \"$name\"？")
            .setMessage("子节点将上移，该节点下的卡片将变为未分类")
            .setPositiveButton("删除") { _, _ ->
                dbHelper.deleteCategory(categoryId)
                if (currentNodeId == categoryId) currentNodeId = null
                loadTree()
                loadCards()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAddRootCategoryDialog() {
        val editText = EditText(this).apply {
            hint = "分类名称"
            setPadding(40, 20, 40, 20)
            textSize = 16f
        }
        AlertDialog.Builder(this)
            .setTitle("新建分类")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                dbHelper.createCategory(editText.text.toString().trim(), null)
                loadTree()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── Multi-select & move ──

    fun enterSelectionMode(cardId: Long) {
        selectionMode = true
        selectedCardIds.add(cardId)
        selectionBar.visibility = View.VISIBLE
        updateSelectionUI()
        adapter.notifyDataSetChanged()
    }

    fun toggleCardSelection(cardId: Long) {
        if (selectedCardIds.contains(cardId)) {
            selectedCardIds.remove(cardId)
            if (selectedCardIds.isEmpty()) {
                exitSelectionMode()
                return
            }
        } else {
            selectedCardIds.add(cardId)
        }
        updateSelectionUI()
        adapter.notifyItemChanged(adapter.currentList.indexOfFirst { it.id == cardId })
    }

    fun isCardSelected(cardId: Long) = selectedCardIds.contains(cardId)
    fun isSelectionMode() = selectionMode

    private fun updateSelectionUI() {
        tvSelectedCount.text = "已选 ${selectedCardIds.size} 张"
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedCardIds.clear()
        selectionBar.visibility = View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun showMoveTreeDialog() {
        val nodes = dbHelper.getCategoryNodes()
        val builder = AlertDialog.Builder(this)
        val scrollView = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        fun addOption(label: String, catId: Long?) {
            val tv = TextView(this).apply {
                text = label
                textSize = 15f
                setPadding(16, 14, 16, 14)
                setOnClickListener {
                    // dismiss and move
                    (it.parent.parent.parent as? AlertDialog)?.dismiss()
                    dbHelper.moveCards(selectedCardIds.toList(), catId)
                    exitSelectionMode()
                    loadTree()
                    loadCards()
                    Toast.makeText(this@MainActivity, "已移动 ${selectedCardIds.size} 张卡片", Toast.LENGTH_SHORT).show()
                }
            }
            container.addView(tv)
        }

        addOption("📚 未分类", null)
        for (node in nodes) {
            val indent = "    ".repeat(node.depth)
            addOption("$indent📂 ${node.category.name}", node.category.id)
        }

        scrollView.addView(container)
        builder
            .setTitle("移动到…")
            .setView(scrollView)
            .setNegativeButton("取消", null)
            .show()
    }

    // ── Batch import ──

    private fun showBatchImportDialog() {
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 12; maxLines = 20
            gravity = Gravity.TOP or Gravity.START
            setPadding(32, 32, 32, 32)
            textSize = 14f
            hint = getString(R.string.batch_import_hint)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(0, 24, 0, 0); addView(editText)
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
                        loadTree(); loadCards()
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
        val cards = mutableListOf<Triple<String, String, String?>>()
        for (line in lines) {
            val parts = line.split("|").map { it.trim() }
            when (parts.size) {
                2 -> {
                    if (parts[0].isNotBlank() && parts[1].isNotBlank())
                        cards.add(Triple(parts[0], parts[1], null))
                }
                3 -> {
                    if (parts[0].isNotBlank() && parts[1].isNotBlank())
                        cards.add(Triple(parts[0], parts[1], parts[2].ifBlank { null }))
                }
            }
        }
        return if (cards.isEmpty()) 0 else dbHelper.insertCards(cards)
    }

    // ── RecyclerView ──

    private fun setupRecyclerView() {
        adapter = CardAdapter(
            activity = this,
            onEditClick = { card ->
                if (selectionMode) return@CardAdapter
                startActivity(Intent(this, EditCardActivity::class.java).apply {
                    putExtra(EditCardActivity.EXTRA_CARD_ID, card.id)
                    putExtra(EditCardActivity.EXTRA_CARD_FRONT, card.front)
                    putExtra(EditCardActivity.EXTRA_CARD_BACK, card.back)
                    putExtra(EditCardActivity.EXTRA_CATEGORY_ID, card.categoryId ?: -1L)
                })
            },
            onDeleteClick = { card ->
                if (selectionMode) return@CardAdapter
                AlertDialog.Builder(this)
                    .setTitle("删除")
                    .setMessage(getString(R.string.confirm_delete))
                    .setPositiveButton("确定") { _, _ ->
                        dbHelper.deleteCard(card.id)
                        loadTree(); loadCards()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            },
            onLongClick = { card ->
                if (!selectionMode) enterSelectionMode(card.id)
                else toggleCardSelection(card.id)
            },
            onClick = { card ->
                if (selectionMode) {
                    toggleCardSelection(card.id)
                } else {
                    startActivity(Intent(this, ReviewActivity::class.java).apply {
                        if (currentNodeId != null) {
                            putExtra(ReviewActivity.EXTRA_CATEGORY_ID, currentNodeId)
                            putExtra(ReviewActivity.EXTRA_INCLUDE_CHILDREN, includeChildren)
                        }
                        putExtra(ReviewActivity.EXTRA_START_CARD_ID, card.id)
                    })
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadCards() {
        val cards = dbHelper.getAllCards(categoryId = currentNodeId, includeChildren = includeChildren)
        adapter.submitList(cards)

        if (cards.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (currentNodeId == null) getString(R.string.no_cards) else "此分类暂无卡片"
            recyclerView.visibility = View.GONE
            btnStartReview.isEnabled = false
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            btnStartReview.isEnabled = true
        }
        val reviewCards = dbHelper.getCardsForReview(categoryId = currentNodeId, includeChildren = includeChildren)
        btnStartReview.text = if (reviewCards.isEmpty()) "暂无待复习卡片" else "开始复习 (${reviewCards.size})"
    }

    override fun onResume() {
        super.onResume()
        loadTree()
        loadCards()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
