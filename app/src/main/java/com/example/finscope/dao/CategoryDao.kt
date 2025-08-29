package com.example.finscope.dao

import androidx.room.*
import com.example.finscope.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT * FROM categories WHERE user_id = :userId ORDER BY name ASC")
    fun getAllCategories(userId: Int = 1): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE user_id = :userId AND type = :categoryType ORDER BY name ASC")
    fun getCategoriesByType(categoryType: String, userId: Int = 1): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE category_id = :categoryId AND user_id = :userId")
    fun getCategoryById(categoryId: Int, userId: Int = 1): Flow<Category?>
}