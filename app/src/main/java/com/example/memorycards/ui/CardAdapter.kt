package com.example.memorycards.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.memorycards.R
import com.example.memorycards.databinding.ItemCardBinding
import com.example.memorycards.model.Card

class CardAdapter(
    private val onEditClick: (Card) -> Unit,
    private val onDeleteClick: (Card) -> Unit
) : ListAdapter<Card, CardAdapter.CardViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CardViewHolder(private val binding: ItemCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(card: Card) {
            binding.tvFront.text = card.front
            binding.tvBack.text = card.back
            binding.tvBox.text = "箱 ${card.box}/5"

            if (card.category.isNotEmpty()) {
                binding.tvCategory.visibility = android.view.View.VISIBLE
                binding.tvCategory.text = card.category
            } else {
                binding.tvCategory.visibility = android.view.View.GONE
            }

            binding.btnEdit.setOnClickListener {
                onEditClick(card)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(card)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Card>() {
        override fun areItemsTheSame(oldItem: Card, newItem: Card): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Card, newItem: Card): Boolean {
            return oldItem == newItem
        }
    }
}
