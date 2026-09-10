package com.example.finscope.repository

import com.example.finscope.dao.CategoryDao
import com.example.finscope.dao.TransactionDao
import com.example.finscope.dao.UserDao
import com.example.finscope.model.Category
import com.example.finscope.model.Transaction
import com.example.finscope.model.User
import com.example.finscope.network.MonobankClient
import com.example.finscope.network.MonoTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.Date
import java.util.Calendar

class FinanceRepository(
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) {

    // --- Monobank Integration ---

    suspend fun syncWithMonobank(apiToken: String, userId: Int = 1) {
        withContext(Dispatchers.IO) {
            val to = System.currentTimeMillis() / 1000
            val from = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -30)
            }.timeInMillis / 1000

            try {
                val monoTransactions = MonobankClient.api.getTransactions(apiToken, from = from, to = to)
                val allCategories = categoryDao.getAllCategories(userId).firstOrNull() ?: emptyList()
                var totalBalanceChange = BigDecimal.ZERO

                for (monoTx in monoTransactions) {
                    val existingTx = transactionDao.getTransactionByExternalId(monoTx.id)
                    if (existingTx == null) {
                        val newTransaction = convertToTransactionEntity(monoTx, userId, allCategories)
                        transactionDao.insertTransaction(newTransaction)

                        totalBalanceChange += if (newTransaction.type == "дохід") {
                            newTransaction.amount
                        } else {
                            newTransaction.amount.negate()
                        }
                    }
                }

                if (totalBalanceChange != BigDecimal.ZERO) {
                    val currentUser = userDao.getUser(userId).firstOrNull()
                    if (currentUser != null) {
                        val newBalance = currentUser.balance.add(totalBalanceChange)
                        userDao.updateUserBalance(newBalance, userId)
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun convertToTransactionEntity(monoTx: MonoTransaction, userId: Int, categories: List<Category>): Transaction {
        val amount = BigDecimal(monoTx.amount).divide(BigDecimal(100))
        val transactionType = if (amount >= BigDecimal.ZERO) "дохід" else "витрата"
        val category = findCategory(monoTx, categories)

        return Transaction(
            user_id = userId,
            category_id = category?.category_id,
            amount = amount.abs(),
            type = transactionType,
            description = monoTx.description,
            date = Date(monoTx.time * 1000),
            source = "monobank",
            external_id = monoTx.id
        )
    }

    private fun findCategory(monoTx: MonoTransaction, categories: List<Category>): Category? {
        val description = monoTx.description.lowercase()
        for (category in categories) {
            category.keywords?.split(",")?.forEach { keyword ->
                if (description.contains(keyword.trim().lowercase())) {
                    return category
                }
            }
        }
        return null
    }

    // --- Existing Methods ---

    fun getUser(userId: Int = 1): Flow<User?> = userDao.getUser(userId)
    suspend fun insertOrUpdateUser(user: User) = userDao.insertOrUpdateUser(user)
    suspend fun updateUserBalance(newBalance: BigDecimal, userId: Int = 1) = userDao.updateUserBalance(newBalance, userId)

    fun getAllCategories(userId: Int = 1): Flow<List<Category>> = categoryDao.getAllCategories(userId)
    fun getCategoriesByType(categoryType: String, userId: Int = 1): Flow<List<Category>> = categoryDao.getCategoriesByType(categoryType, userId)
    fun getCategoryById(categoryId: Int, userId: Int = 1): Flow<Category?> = categoryDao.getCategoryById(categoryId, userId)
    suspend fun insertCategory(category: Category): Long = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)

    fun getAllTransactions(userId: Int = 1): Flow<List<Transaction>> = transactionDao.getAllTransactions(userId)
    fun getTransactionById(transactionId: Int, userId: Int = 1): Flow<Transaction?> = transactionDao.getTransactionById(transactionId, userId)
    fun getTransactionsByPeriod(startDate: Date, endDate: Date, userId: Int = 1): Flow<List<Transaction>> = transactionDao.getTransactionsByPeriod(startDate, endDate, userId)
    fun getTransactionsByCategory(categoryId: Int, userId: Int = 1): Flow<List<Transaction>> = transactionDao.getTransactionsByCategory(categoryId, userId)
    fun getTransactionsByCategoryAndPeriod(categoryId: Int, startDate: Date, endDate: Date, userId: Int = 1): Flow<List<Transaction>> = transactionDao.getTransactionsByCategoryAndPeriod(categoryId, startDate, endDate, userId)
    fun getTransactionsByType(transactionType: String, userId: Int = 1): Flow<List<Transaction>> = transactionDao.getTransactionsByType(transactionType, userId)
    suspend fun insertTransaction(transaction: Transaction): Long = transactionDao.insertTransaction(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionDao.updateTransaction(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.deleteTransaction(transaction)
    fun getRecentTransactions(userId: Int = 1, limit: Int): Flow<List<Transaction>> = transactionDao.getRecentTransactions(userId, limit)
    suspend fun updateTransactionsType(categoryId: Int, newType: String, userId: Int = 1) = transactionDao.updateTransactionsType(categoryId, newType, userId)
}
