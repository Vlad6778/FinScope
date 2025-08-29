package com.example.finscope.model

import com.google.gson.annotations.SerializedName

data class ExchangeRate(
    @SerializedName("ccy")
    val currencyCode: String,

    @SerializedName("base_ccy")
    val baseCurrencyCode: String,

    @SerializedName("buy")
    val buyRate: String,

    @SerializedName("sale")
    val saleRate: String
)