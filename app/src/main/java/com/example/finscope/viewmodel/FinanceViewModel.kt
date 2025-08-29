package com.example.finscope.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.finscope.AppDatabase
import com.example.finscope.model.Category
import com.example.finscope.model.Transaction
import com.example.finscope.model.User
import com.example.finscope.model.ExchangeRate
import com.example.finscope.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransactionTypes {
    const val INCOME = "дохід"
    const val EXPENSE = "витрата"
}

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val defaultUserId = 1
    private val RECENT_TRANSACTIONS_LIMIT = 3

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    val recentTransactions: LiveData<List<Transaction>>
    val allCategories: LiveData<List<Category>>
    val allTransactionsForHistory: LiveData<List<Transaction>>

    private val _exchangeRates = MutableLiveData<List<ExchangeRate>>()
    val exchangeRates: LiveData<List<ExchangeRate>> = _exchangeRates

    private val _networkError = MutableLiveData<String?>()
    val networkError: LiveData<String?> = _networkError

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FinanceRepository(database.userDao(), database.categoryDao(), database.transactionDao())

        viewModelScope.launch(Dispatchers.IO) {
            var user = repository.getUser(defaultUserId).firstOrNull()
            if (user == null) {
                user = User(user_id = defaultUserId, balance = BigDecimal.ZERO)
                repository.insertOrUpdateUser(user)
            }
            launch(Dispatchers.Main) {
                _currentUser.value = user
            }

            val existingCategories = repository.getAllCategories(defaultUserId).firstOrNull()
            if (existingCategories.isNullOrEmpty()) {
                addDefaultCategories()
            }
        }

        recentTransactions = repository.getRecentTransactions(defaultUserId, RECENT_TRANSACTIONS_LIMIT).asLiveData()
        allCategories = repository.getAllCategories(defaultUserId).asLiveData()
        allTransactionsForHistory = repository.getAllTransactions(defaultUserId).asLiveData()

        viewModelScope.launch {
            repository.getUser(defaultUserId).collect { user ->
                _currentUser.postValue(user)
            }
        }
    }

    private suspend fun addDefaultCategories() {
        val defaultIncomeCategories = listOf(
            Category(user_id = defaultUserId, name = "Зарплата", type = TransactionTypes.INCOME),
            Category(user_id = defaultUserId, name = "Стипендія", type = TransactionTypes.INCOME),
            Category(user_id = defaultUserId, name = "Подарунок", type = TransactionTypes.INCOME),
            Category(user_id = defaultUserId, name = "Аванс", type = TransactionTypes.INCOME),
            Category(user_id = defaultUserId, name = "Премія", type = TransactionTypes.INCOME),
            Category(user_id = defaultUserId, name = "Відсотки", type = TransactionTypes.INCOME),
        )
        val defaultExpenseCategories = listOf(
            Category(user_id = defaultUserId, name = "Продукти", type = TransactionTypes.EXPENSE),
            Category(user_id = defaultUserId, name = "Медицина", type = TransactionTypes.EXPENSE),
            Category(user_id = defaultUserId, name = "Розваги", type = TransactionTypes.EXPENSE),
            Category(user_id = defaultUserId, name = "Транспорт", type = TransactionTypes.EXPENSE),
            Category(user_id = defaultUserId, name = "Комунальні", type = TransactionTypes.EXPENSE),
            Category(user_id = defaultUserId, name = "Одяг/Взуття", type = TransactionTypes.EXPENSE),
            Category(user_id = defaultUserId, name = "Побут", type = TransactionTypes.EXPENSE),
            Category(user_id = defaultUserId, name = "Освіта", type = TransactionTypes.EXPENSE),
            Category(user_id = defaultUserId, name = "Подорожі", type = TransactionTypes.EXPENSE),
            Category(user_id = defaultUserId, name = "Подарунки іншим", type = TransactionTypes.EXPENSE),
        )
        defaultIncomeCategories.forEach { repository.insertCategory(it) }
        defaultExpenseCategories.forEach { repository.insertCategory(it) }
    }

    fun addTransactionAndUpdateBalance(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTransaction(transaction)
            val user = repository.getUser(defaultUserId).firstOrNull()
            if (user != null) {
                val newBalance = if (transaction.type == TransactionTypes.INCOME) {
                    user.balance.add(transaction.amount)
                } else {
                    user.balance.subtract(transaction.amount)
                }
                repository.updateUserBalance(newBalance, defaultUserId)
                _currentUser.postValue(user.copy(balance = newBalance))
            }
        }
    }

    fun getTransactionById(id: Int): LiveData<Transaction?> {
        return repository.getTransactionById(id, defaultUserId).asLiveData()
    }

    fun updateTransactionAndAdjustBalance(originalTransaction: Transaction, updatedTransaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            val transactionToUpdate = updatedTransaction.copy(transaction_id = originalTransaction.transaction_id, user_id = defaultUserId)
            repository.updateTransaction(transactionToUpdate)

            val user = repository.getUser(defaultUserId).firstOrNull()
            if (user != null) {
                var newBalance = user.balance
                if (originalTransaction.type == TransactionTypes.INCOME) {
                    newBalance = newBalance.subtract(originalTransaction.amount)
                } else {
                    newBalance = newBalance.add(originalTransaction.amount)
                }
                if (updatedTransaction.type == TransactionTypes.INCOME) {
                    newBalance = newBalance.add(updatedTransaction.amount)
                } else {
                    newBalance = newBalance.subtract(updatedTransaction.amount)
                }
                repository.updateUserBalance(newBalance, defaultUserId)
            }
        }
    }

    fun deleteTransactionAndUpdateBalance(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = repository.getUser(defaultUserId).firstOrNull()
            if (user != null) {
                repository.deleteTransaction(transaction)
                val newBalance = if (transaction.type == TransactionTypes.INCOME) {
                    user.balance.subtract(transaction.amount)
                } else {
                    user.balance.add(transaction.amount)
                }
                repository.updateUserBalance(newBalance, defaultUserId)
            }
        }
    }

    fun addCustomCategory(categoryName: String, categoryType: String) {
        if (categoryName.isNotBlank()) {
            val category = Category(
                user_id = defaultUserId,
                name = categoryName,
                type = categoryType
            )
            viewModelScope.launch(Dispatchers.IO) {
                repository.insertCategory(category)
            }
        }
    }

    fun getCategoriesByType(type: String): LiveData<List<Category>> {
        return repository.getCategoriesByType(type, defaultUserId).asLiveData()
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            val oldCategory = repository.getCategoryById(category.category_id, defaultUserId).firstOrNull()
            repository.updateCategory(category)
            if (oldCategory != null && oldCategory.type != category.type) {
                val transactions = repository.getTransactionsByCategory(category.category_id, defaultUserId).firstOrNull() ?: emptyList()
                if (transactions.isNotEmpty()) {
                    repository.updateTransactionsType(category.category_id, category.type, defaultUserId)
                    val user = repository.getUser(defaultUserId).firstOrNull()
                    if (user != null) {
                        var newBalance = user.balance
                        transactions.forEach { transaction ->
                            if (oldCategory.type == TransactionTypes.INCOME) {
                                newBalance = newBalance.subtract(transaction.amount)
                            } else {
                                newBalance = newBalance.add(transaction.amount)
                            }
                            if (category.type == TransactionTypes.INCOME) {
                                newBalance = newBalance.add(transaction.amount)
                            } else {
                                newBalance = newBalance.subtract(transaction.amount)
                            }
                        }
                        repository.updateUserBalance(newBalance, defaultUserId)
                    }
                }
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCategory(category)
        }
    }

    fun fetchExchangeRates() {
        viewModelScope.launch(Dispatchers.IO) {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val currentDate = dateFormat.format(Date())
            val result = repository.getExchangeRates(currentDate)
            result.onSuccess { rates ->
                _exchangeRates.postValue(rates)
                _networkError.postValue(null)
            }.onFailure { exception ->
                _networkError.postValue("Помилка завантаження даних: ${exception.message}")
            }
        }
    }

    fun onNetworkErrorShown() {
        _networkError.value = null
    }
}
