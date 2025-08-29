package com.example.finscope.network

import com.example.finscope.model.ExchangeRate
import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("p24api/exchange_rates?json")
    suspend fun getExchangeRates(@Query("date") date: String): JsonElement
}