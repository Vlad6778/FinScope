package com.example.finscope

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.finscope.dao.CategoryDao
import com.example.finscope.dao.TransactionDao
import com.example.finscope.dao.UserDao
import com.example.finscope.model.Category
import com.example.finscope.model.Converters
import com.example.finscope.model.Transaction
import com.example.finscope.model.User

@Database(entities = [User::class, Category::class, Transaction::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finscope_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}