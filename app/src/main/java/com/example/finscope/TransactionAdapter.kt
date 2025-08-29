package com.example.finscope

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.finscope.model.Category
import com.example.finscope.model.Transaction
import com.example.finscope.viewmodel.FinanceViewModel
import com.example.finscope.viewmodel.TransactionTypes
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private val viewModel: FinanceViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val showActionButtons: Boolean, // Новий прапорець
    private val onEditClick: ((Transaction) -> Unit)?,
    private val onDeleteClick: ((Transaction) -> Unit)?
) : ListAdapter<Transaction, TransactionAdapter.ViewHolder>(TransactionDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("uk", "UA"))
    private var categories: List<Category> = emptyList()

    init {
        viewModel.allCategories.observe(lifecycleOwner) { categoryList ->
            categories = categoryList
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryNameTextView: TextView = itemView.findViewById(R.id.tv_category_name)
        val descriptionTextView: TextView = itemView.findViewById(R.id.tv_transaction_description)
        val amountTextView: TextView = itemView.findViewById(R.id.tv_transaction_amount)
        val dateTextView: TextView = itemView.findViewById(R.id.tv_transaction_date)
        val editButton: ImageButton = itemView.findViewById(R.id.button_edit_transaction)
        val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_transaction)

        fun bind(
            transaction: Transaction,
            dateFormat: SimpleDateFormat,
            currencyFormat: NumberFormat,
            categoryName: String?,
            showActionButtons: Boolean,
            onEditClick: ((Transaction) -> Unit)?,
            onDeleteClick: ((Transaction) -> Unit)?
        ) {
            categoryNameTextView.text = categoryName ?: "Без категорії"
            dateTextView.text = dateFormat.format(transaction.date)

            descriptionTextView.text = transaction.description
            descriptionTextView.visibility = if (transaction.description.isNullOrBlank()) View.GONE else View.VISIBLE

            val amountPrefix = if (transaction.type == TransactionTypes.INCOME) "+" else "-"
            amountTextView.text = "$amountPrefix${currencyFormat.format(transaction.amount)}"
            amountTextView.setTextColor(
                if (transaction.type == TransactionTypes.INCOME) Color.parseColor("#4CAF50")
                else Color.parseColor("#F44336")
            )

            if (showActionButtons) {
                editButton.visibility = View.VISIBLE
                deleteButton.visibility = View.VISIBLE
                editButton.setOnClickListener { onEditClick?.invoke(transaction) }
                deleteButton.setOnClickListener { onDeleteClick?.invoke(transaction) }
            } else {
                editButton.visibility = View.GONE
                deleteButton.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = getItem(position)
        val categoryName = categories.find { it.category_id == transaction.category_id }?.name
        holder.bind(transaction, dateFormat, currencyFormat, categoryName, showActionButtons, onEditClick, onDeleteClick)
    }
}

class TransactionDiffCallback : DiffUtil.ItemCallback<Transaction>() {
    override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem.transaction_id == newItem.transaction_id
    }

    override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction): Boolean {
        return oldItem == newItem
    }
}
