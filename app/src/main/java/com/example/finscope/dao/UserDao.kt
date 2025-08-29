package com.example.finscope.dao

import androidx.room.*
import com.example.finscope.model.User
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Dao
interface UserDao {

    @Upsert
    suspend fun insertOrUpdateUser(user: User)

    @Query("SELECT * FROM users WHERE user_id = :userId")
    fun getUser(userId: Int = 1): Flow<User?>

    @Query("UPDATE users SET balance = :newBalance WHERE user_id = :userId")
    suspend fun updateUserBalance(newBalance: BigDecimal, userId: Int = 1):Int

}