package com.example.finscope.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Defines the API endpoints for Monobank.
 */
interface MonobankApi {

    /**
     * Fetches a list of transactions for a specific account and period.
     *
     * @param token The user's personal API token.
     * @param accountId The account ID (e.g., "0" for the default account).
     * @param from The start of the period in Unix timestamp format (seconds).
     * @param to The end of the period in Unix timestamp format (seconds).
     * @return A list of Monobank transactions.
     */
    @GET("personal/statement/{accountId}/{from}/{to}")
    suspend fun getTransactions(
        @Header("X-Token") token: String,
        @Path("accountId") accountId: String = "0",
        @Path("from") from: Long,
        @Path("to") to: Long
    ): List<MonoTransaction>
}