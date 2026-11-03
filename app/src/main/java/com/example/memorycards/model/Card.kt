package com.example.memorycards.model

data class Card(
    val id: Long = 0,
    val front: String,
    val back: String,
    val categoryId: Long? = null,
    val categoryPath: String = "",
    val box: Int = 1,
    val nextReview: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val deepResearch: String = ""
)
