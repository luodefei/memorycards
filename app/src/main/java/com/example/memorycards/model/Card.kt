package com.example.memorycards.model

data class Card(
    val id: Long = 0,
    val front: String,
    val back: String,
    val box: Int = 1,          // Leitner box 1-5
    val nextReview: Long = 0,  // timestamp for next review
    val createdAt: Long = System.currentTimeMillis()
)
