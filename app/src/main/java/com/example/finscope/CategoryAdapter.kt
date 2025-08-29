package com.example.finscope

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finscope.model.Category
import com.example.finscope.viewmodel.TransactionTypes
import com.google.android.material.chip.Chip

class CategoryAdapter(
    private val context: Context,
    private val onEditClick: (Category) -> Unit,
    private val onDeleteClick: (Category) -> Unit
) : ListAdapter<Category, CategoryAdapter.ViewHolder>(CategoryDiffCallback()) {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryNameTextView: TextView = itemView.findViewById(R.id.tv_category_item_name)
        val categoryTypeChip: Chip = itemView.findViewById(R.id.chip_category_item_type)
        val editButton: ImageButton = itemView.findViewById(R.id.button_edit_category)
        val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_category)

        fun bind(
            category: Category,
            context: Context,
            onEditClick: (Category) -> Unit,
            onDeleteClick: (Category) -> Unit
        ) {
            categoryNameTextView.text = category.name
            categoryTypeChip.text = category.type.uppercase()

            if (category.type == TransactionTypes.INCOME) {
                categoryTypeChip.setChipBackgroundColorResource(R.color.income_color_chip)
                categoryTypeChip.setTextColor(ContextCompat.getColor(context, R.color.text_color_income_chip))
            } else {
                categoryTypeChip.setChipBackgroundColorResource(R.color.expense_color_chip)
                categoryTypeChip.setTextColor(ContextCompat.getColor(context, R.color.text_color_expense_chip))
            }

            editButton.setOnClickListener { onEditClick(category) }
            deleteButton.setOnClickListener { onDeleteClick(category) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = getItem(position)
        holder.bind(category, context, onEditClick, onDeleteClick)
    }
}

class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
    override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean {
        return oldItem.category_id == newItem.category_id
    }

    override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean {
        return oldItem == newItem
    }
}