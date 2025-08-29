package com.example.finscope

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.finscope.R
import com.example.finscope.model.ExchangeRate

class CurrencyAdapter(private var rates: List<ExchangeRate>) :
    RecyclerView.Adapter<CurrencyAdapter.CurrencyViewHolder>() {

    class CurrencyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val currencyPair: TextView = itemView.findViewById(R.id.tv_currency_pair)
        private val buyRate: TextView = itemView.findViewById(R.id.tv_buy_rate)
        private val saleRate: TextView = itemView.findViewById(R.id.tv_sale_rate)

        fun bind(rate: ExchangeRate) {
            currencyPair.text = "${rate.currencyCode} / ${rate.baseCurrencyCode}"
            buyRate.text = "Купівля: ${rate.buyRate}"
            saleRate.text = "Продаж: ${rate.saleRate}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CurrencyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_currency, parent, false)
        return CurrencyViewHolder(view)
    }

    override fun onBindViewHolder(holder: CurrencyViewHolder, position: Int) {
        holder.bind(rates[position])
    }

    override fun getItemCount(): Int = rates.size

    fun updateData(newRates: List<ExchangeRate>) {
        rates = newRates
        notifyDataSetChanged()
    }
}