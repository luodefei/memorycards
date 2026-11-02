package com.example.memorycards.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.memorycards.R
import com.example.memorycards.data.DatabaseHelper
import com.example.memorycards.model.Card

class ReviewActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReviewActivity"
        const val EXTRA_CATEGORY_ID = "review_category_id"
        const val EXTRA_INCLUDE_CHILDREN = "include_children"
        const val EXTRA_START_CARD_ID = "start_card_id"
    }

    private lateinit var dbHelper: DatabaseHelper
    private var reviewList: List<Card> = emptyList()
    private var currentIndex = 0
    private var isShowingBack = false
    private var reviewCategoryId: Long? = null
    private var includeChildren = false
    private var startCardId: Long? = null

    private lateinit var cardHeader: android.view.View
    private lateinit var cardFaceIcon: android.widget.ImageView
    private lateinit var progressText: android.widget.TextView
    private lateinit var cardContent: android.widget.TextView
    private lateinit var flipCard: CardView
    private lateinit var btnCrown: android.widget.ImageButton
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
        btnCrown = findViewById(R.id.btnCrown)
        btnForgot = findViewById(R.id.btnForgot)
        btnRemember = findViewById(R.id.btnRemember)

        flipCard.setOnClickListener { flipCard() }
        btnForgot.setOnClickListener { onAnswer(false) }
        btnRemember.setOnClickListener { onAnswer(true) }
        btnCrown.setOnClickListener {
            android.widget.Toast.makeText(this, "深度研究即将上线", android.widget.Toast.LENGTH_SHORT).show()
        }

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
        isShowingBack = false
        val card = reviewList[currentIndex]
        cardHeader.visibility = View.VISIBLE
        progressText.text = "${currentIndex + 1}/${reviewList.size}"
        cardFaceIcon.setImageResource(R.drawable.ic_card_front)

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
        val card = reviewList[currentIndex]
        val anim = AnimationUtils.loadAnimation(this, R.anim.flip_vertical)
        flipCard.startAnimation(anim)

        progressText.text = "${currentIndex + 1}/${reviewList.size}"
        if (isShowingBack) {
            cardFaceIcon.setImageResource(R.drawable.ic_card_front)
            cardContent.text = card.front
            cardContent.setTextColor(getColor(R.color.black))
            flipCard.setCardBackgroundColor(getColor(R.color.card_front_bg))
        } else {
            cardFaceIcon.setImageResource(R.drawable.ic_card_back)
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
        if (currentIndex >= reviewList.size) showComplete() else showCard()
    }

    private fun showComplete() {
        cardHeader.visibility = View.GONE
        cardContent.text = getString(R.string.review_complete)
        cardContent.setTextColor(getColor(R.color.purple_700))
        flipCard.setCardBackgroundColor(getColor(R.color.white))
        btnForgot.isEnabled = false
        btnRemember.isEnabled = false
    }
}
