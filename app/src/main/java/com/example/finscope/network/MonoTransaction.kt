package com.example.finscope.network

import com.google.gson.annotations.SerializedName

/**
 * Represents a single transaction from the Monobank API.
 * API Docs: https://api.monobank.ua/docs/#/default/get_personal_statement__account___from___to_
 */
data class MonoTransaction(
    @SerializedName("id")
    val id: String,

    @SerializedName("time")
    val time: Long, // Unix timestamp

    @SerializedName("description")
    val description: String,

    @SerializedName("mcc")
    val mcc: Int, // Merchant Category Code

    @SerializedName("amount")
    val amount: Long, // Amount in cents

    @SerializedName("currencyCode")
    val currencyCode: Int // ISO 4217 currency code
)