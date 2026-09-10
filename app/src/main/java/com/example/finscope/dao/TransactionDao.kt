package com.example.finscope.dao

import androidx.room.*
import com.example.finscope.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY date DESC")
    fun getAllTransactions(userId: Int = 1): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE transaction_id = :transactionId AND user_id = :userId")
    fun getTransactionById(transactionId: Int, userId: Int = 1): Flow<Transaction?>

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsByPeriod(startDate: Date, endDate: Date, userId: Int = 1): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND category_id = :categoryId ORDER BY date DESC")
    fun getTransactionsByCategory(categoryId: Int, userId: Int = 1): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND type = :transactionType ORDER BY date DESC")
    fun getTransactionsByType(transactionType: String, userId: Int = 1): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE user_id = :userId AND category_id = :categoryId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getTransactionsByCategoryAndPeriod(categoryId: Int, startDate: Date, endDate: Date, userId: Int = 1): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY date DESC LIMIT :limit")
    fun getRecentTransactions(userId: Int = 1, limit: Int): Flow<List<Transaction>>

    @Query("UPDATE transactions SET type = :newType WHERE category_id = :categoryId AND user_id = :userId")
    suspend fun updateTransactionsType(categoryId: Int, newType: String, userId: Int = 1)

    @Query("SELECT * FROM transactions WHERE external_id = :externalId LIMIT 1")
    suspend fun getTransactionByExternalId(externalId: String): Transaction?
}