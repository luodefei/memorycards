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
    private val activity: MainActivity,
    private val onEditClick: (Card) -> Unit,
    private val onDeleteClick: (Card) -> Unit,
    private val onLongClick: (Card) -> Unit,
    private val onClick: (Card) -> Unit
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

            // Checkbox for selection mode
            if (activity.isSelectionMode()) {
                binding.checkbox.visibility = android.view.View.VISIBLE
                binding.checkbox.isChecked = activity.isCardSelected(card.id)
                binding.btnEdit.isEnabled = false
                binding.btnDelete.isEnabled = false
            } else {
                binding.checkbox.visibility = android.view.View.GONE
                binding.btnEdit.isEnabled = true
                binding.btnDelete.isEnabled = true
            }

            if (card.categoryPath.isNotEmpty()) {
                binding.tvCategory.visibility = android.view.View.VISIBLE
                binding.tvCategory.text = card.categoryPath
            } else {
                binding.tvCategory.visibility = android.view.View.GONE
            }

            if (activity.isCardSelected(card.id)) {
                binding.root.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.selected_card_bg))
            } else {
                binding.root.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.white))
            }

            binding.root.setOnClickListener { onClick(card) }
            binding.root.setOnLongClickListener {
                onLongClick(card)
                true
            }

            binding.btnEdit.setOnClickListener { onEditClick(card) }
            binding.btnDelete.setOnClickListener { onDeleteClick(card) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Card>() {
        override fun areItemsTheSame(oldItem: Card, newItem: Card) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Card, newItem: Card) = oldItem == newItem
    }
}
