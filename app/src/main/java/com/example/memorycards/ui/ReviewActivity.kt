package com.example.memorycards.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.memorycards.R
import com.example.memorycards.data.Api
import com.example.memorycards.data.DatabaseHelper
import com.example.memorycards.model.Card
import org.json.JSONObject

class ReviewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReviewActivity"
        const val EXTRA_CATEGORY_ID = "review_category_id"
        const val EXTRA_INCLUDE_CHILDREN = "include_children"
        const val EXTRA_START_CARD_ID = "start_card_id"

        private const val PAGE_FRONT = 0
        private const val PAGE_BACK = 1
        private const val PAGE_RESEARCH = 2
    }

    private lateinit var dbHelper: DatabaseHelper
    private var reviewList: List<Card> = emptyList()
    private var currentIndex = 0
    private var currentPage = PAGE_FRONT
    private var researchLoading = false
    private var reviewCategoryId: Long? = null
    private var includeChildren = false
    private var startCardId: Long? = null

    private lateinit var cardHeader: android.view.View
    private lateinit var cardFaceIcon: android.widget.ImageView
    private lateinit var progressText: android.widget.TextView
    private lateinit var cardContent: android.widget.TextView
    private lateinit var flipCard: CardView
    private lateinit var researchCard: CardView
    private lateinit var tvResearchContent: android.widget.TextView
    private lateinit var btnResearch: android.widget.Button
    private lateinit var btnDeepResearch: android.widget.TextView
    private lateinit var btnForgot: android.widget.Button
    private lateinit var btnRemember: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        dbHelper = DatabaseHelper(this)
        reviewCategoryId = if (intent.hasExtra(EXTRA_CATEGORY_ID)) intent.getLongExtra(EXTRA_CATEGORY_ID, -1).let { if (it == -1L) null else it } else null
        includeChildren = intent.getBooleanExtra(EXTRA_INCLUDE_CHILDREN, false)
        startCardId = intent.getLongExtra(EXTRA_START_CARD_ID, -1L).takeIf { it != -1L }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        cardHeader = findViewById(R.id.cardHeader)
        cardFaceIcon = findViewById(R.id.ivCardFace)
        progressText = findViewById(R.id.tvProgress)
        cardContent = findViewById(R.id.tvCardContent)
        flipCard = findViewById(R.id.flipCardContainer)
        researchCard = findViewById(R.id.researchCard)
        tvResearchContent = findViewById(R.id.tvResearchContent)
        btnResearch = findViewById(R.id.btnResearch)
        btnDeepResearch = findViewById(R.id.btnDeepResearch)
        btnForgot = findViewById(R.id.btnForgot)
        btnRemember = findViewById(R.id.btnRemember)

        flipCard.setOnClickListener { flipCard() }
        btnForgot.setOnClickListener { onAnswer(false) }
        btnRemember.setOnClickListener { onAnswer(true) }
        btnDeepResearch.setOnClickListener { onDeepResearchEntry() }
        btnResearch.setOnClickListener { startResearch() }

        loadReviewCards()
    }

    private fun loadReviewCards() {
        if (startCardId != null) {
            reviewList = dbHelper.getAllCards(categoryId = reviewCategoryId, includeChildren = includeChildren)
            if (reviewList.isEmpty()) {
                showComplete()
                return
            }
            val index = reviewList.indexOfFirst { it.id == startCardId }
            currentIndex = if (index >= 0) index else 0
            showCard()
            return
        }

        reviewList = dbHelper.getCardsForReview(categoryId = reviewCategoryId, includeChildren = includeChildren)
        Log.d(TAG, "getCardsForReview returned ${reviewList.size} cards")

        if (reviewList.isEmpty()) {
            reviewList = dbHelper.getAllCards(categoryId = reviewCategoryId, includeChildren = includeChildren)
            Log.d(TAG, "getAllCards returned ${reviewList.size} cards")
        }

        if (reviewList.isEmpty()) {
            showComplete()
            return
        }

        currentIndex = 0
        showCard()
    }

    private fun showCard() {
        if (currentIndex >= reviewList.size) { showComplete(); return }
        currentPage = PAGE_FRONT
        val card = reviewList[currentIndex]
        cardHeader.visibility = View.VISIBLE
        progressText.text = "${currentIndex + 1}/${reviewList.size}"
        cardFaceIcon.setImageResource(R.drawable.ic_card_front)

        flipCard.visibility = View.VISIBLE
        researchCard.visibility = View.GONE
        btnDeepResearch.visibility = View.VISIBLE
        btnDeepResearch.text = "深度研究"

        cardContent.text = card.front
        cardContent.setTextColor(getColor(R.color.black))
        flipCard.setCardBackgroundColor(getColor(R.color.card_front_bg))

        btnForgot.isEnabled = true
        btnRemember.isEnabled = true
        btnForgot.visibility = View.VISIBLE
        btnRemember.visibility = View.VISIBLE
    }

    private fun flipCard() {
        if (reviewList.isEmpty() || currentIndex >= reviewList.size) return
        if (currentPage == PAGE_RESEARCH) return
        val card = reviewList[currentIndex]
        val anim = AnimationUtils.loadAnimation(this, R.anim.flip_vertical)
        flipCard.startAnimation(anim)

        progressText.text = "${currentIndex + 1}/${reviewList.size}"
        if (currentPage == PAGE_BACK) {
            cardFaceIcon.setImageResource(R.drawable.ic_card_front)
            cardContent.text = card.front
            cardContent.setTextColor(getColor(R.color.black))
            flipCard.setCardBackgroundColor(getColor(R.color.card_front_bg))
            currentPage = PAGE_FRONT
        } else {
            cardFaceIcon.setImageResource(R.drawable.ic_card_back)
            cardContent.text = card.back
            cardContent.setTextColor(getColor(R.color.black))
            flipCard.setCardBackgroundColor(getColor(R.color.card_back_bg))
            currentPage = PAGE_BACK
        }
    }

    private fun onDeepResearchEntry() {
        if (reviewList.isEmpty() || currentIndex >= reviewList.size) return
        if (currentPage == PAGE_RESEARCH) {
            showCard()
            return
        }
        PremiumGate.onPremiumFeature(this) {
            showResearchPage()
        }
    }

    private fun showResearchPage() {
        currentPage = PAGE_RESEARCH
        flipCard.visibility = View.GONE
        researchCard.visibility = View.VISIBLE
        btnDeepResearch.text = "返回卡片"
        renderResearch()
    }

    private fun renderResearch() {
        val card = reviewList[currentIndex]
        if (card.deepResearch.isBlank()) {
            tvResearchContent.text = "点击下方按钮，对这张卡片做深度研究"
            btnResearch.text = "开始深度研究"
        } else {
            tvResearchContent.text = card.deepResearch
            btnResearch.text = "重新研究"
        }
        btnResearch.isEnabled = !researchLoading
    }

    private fun startResearch() {
        if (researchLoading || currentIndex >= reviewList.size) return
        val card = reviewList[currentIndex]
        researchLoading = true
        btnResearch.isEnabled = false
        btnResearch.text = "研究中…"

        val body = JSONObject()
            .put("front", card.front)
            .put("back", card.back)
            .toString()
        Api.request(this, "/api/research/deep", "POST", body,
            callback = object : Api.Callback {
                override fun onSuccess(body: String) {
                    researchLoading = false
                    val result = runCatching { JSONObject(body).getString("result") }.getOrNull()
                    if (result.isNullOrBlank()) {
                        Toast.makeText(this@ReviewActivity, "研究服务未返回内容", Toast.LENGTH_SHORT).show()
                        renderResearch()
                        return
                    }
                    dbHelper.updateCardDeepResearch(card.id, result)
                    reviewList = reviewList.toMutableList().apply {
                        set(currentIndex, card.copy(deepResearch = result))
                    }
                    renderResearch()
                }

                override fun onError(code: Int, message: String) {
                    researchLoading = false
                    Toast.makeText(this@ReviewActivity, message, Toast.LENGTH_SHORT).show()
                    renderResearch()
                }
            },
            readTimeoutMs = 120_000)
    }

    private fun onAnswer(remembered: Boolean) {
        if (reviewList.isEmpty() || currentIndex >= reviewList.size) return
        val card = reviewList[currentIndex]
        dbHelper.updateCardReview(card.id, remembered)
        currentIndex++
        if (currentIndex >= reviewList.size) showComplete() else showCard()
    }

    private fun showComplete() {
        cardHeader.visibility = View.GONE
        cardContent.text = getString(R.string.review_complete)
        cardContent.setTextColor(getColor(R.color.purple_700))
        flipCard.setCardBackgroundColor(getColor(R.color.white))
        flipCard.visibility = View.VISIBLE
        researchCard.visibility = View.GONE
        btnDeepResearch.visibility = View.GONE
        btnForgot.isEnabled = false
        btnRemember.isEnabled = false
    }
}
