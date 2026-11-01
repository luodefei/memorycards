package com.example.memorycards.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.memorycards.R
import com.example.memorycards.data.DatabaseHelper
import com.example.memorycards.model.Card

class ReviewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReviewActivity"
        const val EXTRA_CATEGORY = "review_category"
    }

    private lateinit var dbHelper: DatabaseHelper
    private var reviewList: List<Card> = emptyList()
    private var currentIndex = 0
    private var isShowingBack = false
    private var reviewCategory: String = ""

    private lateinit var cardLabel: android.widget.TextView
    private lateinit var cardContent: android.widget.TextView
    private lateinit var loadedCount: android.widget.TextView
    private lateinit var flipCard: CardView
    private lateinit var btnForgot: android.widget.Button
    private lateinit var btnRemember: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        dbHelper = DatabaseHelper(this)
        reviewCategory = intent.getStringExtra(EXTRA_CATEGORY) ?: ""

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        if (reviewCategory.isNotEmpty()) {
            toolbar.title = "复习 — $reviewCategory"
        }

        cardLabel = findViewById(R.id.tvCardLabel)
        cardContent = findViewById(R.id.tvCardContent)
        loadedCount = findViewById(R.id.tvLoadedCount)
        flipCard = findViewById(R.id.flipCardContainer)
        btnForgot = findViewById(R.id.btnForgot)
        btnRemember = findViewById(R.id.btnRemember)

        flipCard.setOnClickListener { flipCard() }
        btnForgot.setOnClickListener { onAnswer(false) }
        btnRemember.setOnClickListener { onAnswer(true) }

        loadReviewCards()
    }

    private fun loadReviewCards() {
        reviewList = dbHelper.getCardsForReview(categoryFilter = reviewCategory)
        Log.d(TAG, "getCardsForReview(cat=$reviewCategory) returned ${reviewList.size} cards")

        if (reviewList.isEmpty()) {
            // Fallback: show all cards in this category
            reviewList = dbHelper.getAllCards(categoryFilter = reviewCategory)
            Log.d(TAG, "getAllCards(cat=$reviewCategory) returned ${reviewList.size} cards")
        }

        if (reviewList.isEmpty()) {
            showComplete()
            return
        }

        loadedCount.text = "共 ${reviewList.size} 张卡片待复习"
        currentIndex = 0
        showCard()
    }

    private fun showCard() {
        if (currentIndex >= reviewList.size) {
            showComplete()
            return
        }

        isShowingBack = false
        val card = reviewList[currentIndex]
        val progressText = "${currentIndex + 1} / ${reviewList.size}"

        cardLabel.text = "📖 正面  |  $progressText"
        cardContent.text = card.front
        cardContent.setTextColor(getColor(R.color.black))
        flipCard.setCardBackgroundColor(getColor(R.color.card_front_bg))

        btnForgot.isEnabled = true
        btnRemember.isEnabled = true
        btnForgot.visibility = View.VISIBLE
        btnRemember.visibility = View.VISIBLE

        Log.d(TAG, "Showing card ${currentIndex}: front=${card.front}")
    }

    private fun flipCard() {
        if (reviewList.isEmpty() || currentIndex >= reviewList.size) return

        val card = reviewList[currentIndex]
        val anim = AnimationUtils.loadAnimation(this, R.anim.flip_vertical)
        flipCard.startAnimation(anim)

        if (isShowingBack) {
            cardLabel.text = "📖 正面  |  ${currentIndex + 1} / ${reviewList.size}"
            cardContent.text = card.front
            cardContent.setTextColor(getColor(R.color.black))
            flipCard.setCardBackgroundColor(getColor(R.color.card_front_bg))
        } else {
            cardLabel.text = "📝 背面  |  ${currentIndex + 1} / ${reviewList.size}"
            cardContent.text = card.back
            cardContent.setTextColor(getColor(R.color.black))
            flipCard.setCardBackgroundColor(getColor(R.color.card_back_bg))
        }
        isShowingBack = !isShowingBack
    }

    private fun onAnswer(remembered: Boolean) {
        if (reviewList.isEmpty() || currentIndex >= reviewList.size) return

        val card = reviewList[currentIndex]
        dbHelper.updateCardReview(card.id, remembered)

        currentIndex++
        if (currentIndex >= reviewList.size) {
            showComplete()
        } else {
            showCard()
        }
    }

    private fun showComplete() {
        cardLabel.text = "🎉"
        cardContent.text = getString(R.string.review_complete)
        cardContent.setTextColor(getColor(R.color.purple_700))
        flipCard.setCardBackgroundColor(getColor(R.color.white))
        btnForgot.isEnabled = false
        btnRemember.isEnabled = false
        loadedCount.text = ""
    }
}
