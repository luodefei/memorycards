package com.example.memorycards.ui

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.memorycards.R
import com.example.memorycards.data.DatabaseHelper
import com.example.memorycards.databinding.ActivityReviewBinding
import com.example.memorycards.model.Card

class ReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewBinding
    private lateinit var dbHelper: DatabaseHelper
    private var reviewList: List<Card> = emptyList()
    private var currentIndex = 0
    private var isShowingBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper(this)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        loadReviewCards()

        binding.cardView.setOnClickListener {
            flipCard()
        }

        binding.btnForgot.setOnClickListener {
            onAnswer(false)
        }

        binding.btnRemember.setOnClickListener {
            onAnswer(true)
        }
    }

    private fun loadReviewCards() {
        reviewList = dbHelper.getCardsForReview()
        if (reviewList.isEmpty()) {
            // If no cards due for review, show all cards
            reviewList = dbHelper.getAllCards()
        }
        if (reviewList.isEmpty()) {
            showComplete()
            return
        }
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
        binding.tvCardContent.text = card.front
        binding.tvCardLabel.text = "正面"
        binding.tvCardContent.setTextColor(getColor(R.color.black))
        binding.cardView.setCardBackgroundColor(getColor(R.color.card_front_bg))
        binding.tvProgress.text = "${currentIndex + 1} / ${reviewList.size}"
        binding.btnForgot.isEnabled = true
        binding.btnRemember.isEnabled = true
        binding.btnForgot.visibility = View.VISIBLE
        binding.btnRemember.visibility = View.VISIBLE
    }

    private fun flipCard() {
        val card = reviewList[currentIndex]
        val anim = AnimationUtils.loadAnimation(this, R.anim.flip_vertical)
        binding.cardView.startAnimation(anim)

        if (isShowingBack) {
            binding.tvCardContent.text = card.front
            binding.tvCardLabel.text = "正面"
            binding.tvCardContent.setTextColor(getColor(R.color.black))
            binding.cardView.setCardBackgroundColor(getColor(R.color.card_front_bg))
        } else {
            binding.tvCardContent.text = card.back
            binding.tvCardLabel.text = "背面"
            binding.tvCardContent.setTextColor(getColor(R.color.black))
            binding.cardView.setCardBackgroundColor(getColor(R.color.card_back_bg))
        }
        isShowingBack = !isShowingBack
    }

    private fun onAnswer(remembered: Boolean) {
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
        binding.tvCardLabel.text = "🎉"
        binding.tvCardContent.text = getString(R.string.review_complete)
        binding.tvCardContent.setTextColor(getColor(R.color.purple_700))
        binding.cardView.setCardBackgroundColor(getColor(R.color.white))
        binding.btnForgot.isEnabled = false
        binding.btnRemember.isEnabled = false
        binding.tvProgress.text = ""
    }
}
