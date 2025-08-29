package com.example.finscope.repository

import com.example.finscope.dao.CategoryDao
import com.example.finscope.dao.TransactionDao
import com.example.finscope.dao.UserDao
import com.example.finscope.model.Category
import com.example.finscope.model.ExchangeRate
import com.example.finscope.model.Transaction
import com.example.finscope.model.User
import com.example.finscope.network.RetrofitClient
import com.google.gson.JsonElement
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.math.BigDecimal
import java.util.Date

class FinanceRepository(
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) {
    private val apiService = RetrofitClient.apiService

    // Методи для User
    fun getUser(userId: Int = 1): Flow<User?> = userDao.getUser(userId)
    suspend fun insertOrUpdateUser(user: User) = userDao.insertOrUpdateUser(user)
    suspend fun updateUserBalance(newBalance: BigDecimal, userId: Int = 1) {
        println("Updating user balance in DB: userId=$userId, newBalance=$newBalance")
        try {
            userDao.updateUserBalance(newBalance, userId)
            println("updateUserBalance completed")
        } catch (e: Exception) {
            println("Error in updateUserBalance: ${e.message}")
            throw e
        }
    }

    // Методи для Category
    fun getAllCategories(userId: Int = 1): Flow<List<Category>> = categoryDao.getAllCategories(userId)
    fun getCategoriesByType(categoryType: String, userId: Int = 1): Flow<List<Category>> = categoryDao.getCategoriesByType(categoryType, userId)
    fun getCategoryById(categoryId: Int, userId: Int = 1): Flow<Category?> = categoryDao.getCategoryById(categoryId, userId)
    suspend fun insertCategory(category: Category): Long = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)

    // Методи для Transaction
    fun getAllTransactions(userId: Int = 1): Flow<List<Transaction>> = transactionDao.getAllTransactions(userId)
    fun getTransactionById(transactionId: Int, userId: Int = 1): Flow<Transaction?> = transactionDao.getTransactionById(transactionId, userId)
    fun getTransactionsByPeriod(startDate: Date, endDate: Date, userId: Int = 1): Flow<List<Transaction>> = transactionDao.getTransactionsByPeriod(startDate, endDate, userId)
    fun getTransactionsByCategory(categoryId: Int, userId: Int = 1): Flow<List<Transaction>> = transactionDao.getTransactionsByCategory(categoryId, userId)
    fun getTransactionsByType(transactionType: String, userId: Int = 1): Flow<List<Transaction>> = transactionDao.getTransactionsByType(transactionType, userId)
    suspend fun insertTransaction(transaction: Transaction): Long = transactionDao.insertTransaction(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionDao.updateTransaction(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.deleteTransaction(transaction)
    fun getRecentTransactions(userId: Int = 1, limit: Int): Flow<List<Transaction>> =
        transactionDao.getRecentTransactions(userId, limit)
    suspend fun updateTransactionsType(categoryId: Int, newType: String, userId: Int = 1) {
        transactionDao.updateTransactionsType(categoryId, newType, userId)
    }

    // Метод для отримання курсів валют
    suspend fun getExchangeRates(date: String): Result<List<ExchangeRate>> {
        return try {
            val response = apiService.getExchangeRates(date)
            val rates = parseExchangeRates(response)
            Result.success(rates)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Метод для парсингу JsonElement у List<ExchangeRate>
    private fun parseExchangeRates(json: JsonElement): List<ExchangeRate> {
        val exchangeRateArray = json.asJsonObject.getAsJsonArray("exchangeRate")
        return exchangeRateArray.map { rateJson ->
            val rateObj = rateJson.asJsonObject
            ExchangeRate(
                currencyCode = rateObj.get("currency").asString,
                baseCurrencyCode = rateObj.get("baseCurrency").asString,
                buyRate = rateObj.get("purchaseRateNB")?.asString ?: "0.0",
                saleRate = rateObj.get("saleRateNB")?.asString ?: "0.0"
            )
        }
    }
}