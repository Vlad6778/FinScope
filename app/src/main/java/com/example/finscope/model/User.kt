package com.example.finscope.model
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = false)
    val user_id: Int = 1,
    val balance: BigDecimal = BigDecimal.ZERO
)