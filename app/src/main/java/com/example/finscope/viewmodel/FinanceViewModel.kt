package com.example.finscope.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.finscope.AppDatabase
import com.example.finscope.model.Category
import com.example.finscope.model.Transaction
import com.example.finscope.model.User
import com.example.finscope.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Date
import java.util.Calendar
import java.util.concurrent.TimeUnit

object TransactionTypes {
    const val INCOME = "дохід"
    const val EXPENSE = "витрата"
}


data class TrendData(
    val percentageChange: Double,
    val isIncrease: Boolean,
    val dateStart: Date,
    val dateEnd: Date
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FinanceRepository
    private val defaultUserId = 1
    private val RECENT_TRANSACTIONS_LIMIT = 3

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    val recentTransactions: LiveData<List<Transaction>>
    val allCategories: LiveData<List<Category>>
    val allTransactionsForHistory: LiveData<List<Transaction>>

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _syncStatus = MutableLiveData<String?>()
    val syncStatus: LiveData<String?> = _syncStatus

    private val _expenseTrend = MutableLiveData<TrendData?>()
    val expenseTrend: LiveData<TrendData?> = _expenseTrend

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

    // --- Analytics ---

    fun calculateExpenseTrend(currentStartDate: Date, currentEndDate: Date) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = Date()

            // 1. Определяем длительность периода (чтобы понять, что это: месяц, год?)
            val diffMillis = currentEndDate.time - currentStartDate.time
            val daysInPeriod = TimeUnit.MILLISECONDS.toDays(diffMillis) + 1

            val calendar = Calendar.getInstance()
            calendar.time = currentStartDate

            // Сдвигаем назад
            if (daysInPeriod in 28..31) {
                calendar.add(Calendar.MONTH, -1)
            } else if (daysInPeriod in 365..366) {
                calendar.add(Calendar.YEAR, -1)
            } else {
                calendar.add(Calendar.DAY_OF_YEAR, -daysInPeriod.toInt())
            }
            val previousStartDate = calendar.time

            // 2. Определяем, сколько дней прошло В ТЕКУЩЕМ периоде
            val effectiveCurrentEndDate = if (currentEndDate.after(now)) now else currentEndDate
            val millisPassed = effectiveCurrentEndDate.time - currentStartDate.time
            val daysPassed = TimeUnit.MILLISECONDS.toDays(millisPassed).toInt()

            // 3. Вычисляем конец ПРОШЛОГО периода (День-в-День)
            calendar.time = previousStartDate
            calendar.add(Calendar.DAY_OF_YEAR, daysPassed)

            // Конец дня
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            val previousEndDate = calendar.time

            // 4. Запрашиваем данные
            val currentExpenses = repository.getTransactionsByPeriod(currentStartDate, effectiveCurrentEndDate, defaultUserId).firstOrNull()
                ?.filter { it.type == TransactionTypes.EXPENSE }?.sumOf { it.amount } ?: BigDecimal.ZERO

            val previousExpenses = repository.getTransactionsByPeriod(previousStartDate, previousEndDate, defaultUserId).firstOrNull()
                ?.filter { it.type == TransactionTypes.EXPENSE }?.sumOf { it.amount } ?: BigDecimal.ZERO

            // 5. Отправляем результат ВМЕСТЕ с датами
            if (previousExpenses > BigDecimal.ZERO) {
                val change = (currentExpenses - previousExpenses).toDouble()
                val percentageChange = (change / previousExpenses.toDouble()) * 100
                _expenseTrend.postValue(TrendData(percentageChange, change > 0, previousStartDate, previousEndDate))
            } else if (currentExpenses > BigDecimal.ZERO) {
                _expenseTrend.postValue(TrendData(100.0, true, previousStartDate, previousEndDate))
            } else {
                _expenseTrend.postValue(null)
            }
        }
    }

    // ... (ВСЕ ОСТАЛЬНЫЕ МЕТОДЫ ОСТАЮТСЯ БЕЗ ИЗМЕНЕНИЙ) ...

    fun syncWithMonobank(apiToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.syncWithMonobank(apiToken)
                _syncStatus.value = "Синхронізація успішна!"
            } catch (e: Exception) {
                _syncStatus.value = "Помилка синхронізації: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSyncStatusShown() {
        _syncStatus.value = null
    }

    fun getTransactionsByCategoryAndPeriod(categoryId: Int, startDate: Date, endDate: Date): LiveData<List<Transaction>> {
        return repository.getTransactionsByCategoryAndPeriod(categoryId, startDate, endDate, defaultUserId).asLiveData()
    }

    private suspend fun addDefaultCategories() {
        val defaultIncomeCategories = listOf(
            Category(user_id = defaultUserId, name = "Зарплата", type = TransactionTypes.INCOME, keywords = "зарплата, аванс, виплата"),
            Category(user_id = defaultUserId, name = "Стипендія", type = TransactionTypes.INCOME, keywords = "стипендія"),
            Category(user_id = defaultUserId, name = "Подарунок", type = TransactionTypes.INCOME, keywords = "подарунок"),
            Category(user_id = defaultUserId, name = "Аванс", type = TransactionTypes.INCOME, keywords = "аванс"),
            Category(user_id = defaultUserId, name = "Премія", type = TransactionTypes.INCOME, keywords = "премія"),
            Category(user_id = defaultUserId, name = "Відсотки", type = TransactionTypes.INCOME, keywords = "відсотки, депозит"),
        )
        val defaultExpenseCategories = listOf(
            Category(user_id = defaultUserId, name = "Продукти", type = TransactionTypes.EXPENSE, keywords = "сільпо, атб, ашан, продукти, їжа, маркет"),
            Category(user_id = defaultUserId, name = "Медицина", type = TransactionTypes.EXPENSE, keywords = "аптека, ліки, лікар"),
            Category(user_id = defaultUserId, name = "Розваги", type = TransactionTypes.EXPENSE, keywords = "кіно, театр, боулінг, клуб"),
            Category(user_id = defaultUserId, name = "Транспорт", type = TransactionTypes.EXPENSE, keywords = "проїзд, таксі, убер, болт, метро, автобус"),
            Category(user_id = defaultUserId, name = "Комунальні", type = TransactionTypes.EXPENSE, keywords = "світло, газ, вода, інтернет, квартплата"),
            Category(user_id = defaultUserId, name = "Одяг/Взуття", type = TransactionTypes.EXPENSE, keywords = "одяг, взуття, zara, h&m"),
            Category(user_id = defaultUserId, name = "Побут", type = TransactionTypes.EXPENSE, keywords = "хімія, дім, ремонт"),
            Category(user_id = defaultUserId, name = "Освіта", type = TransactionTypes.EXPENSE, keywords = "курси, навчання, книги"),
            Category(user_id = defaultUserId, name = "Подорожі", type = TransactionTypes.EXPENSE, keywords = "квитки, готель, поїзд, літак"),
            Category(user_id = defaultUserId, name = "Подарунки іншим", type = TransactionTypes.EXPENSE, keywords = "квіти, подарунок"),
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

    fun getTransactionsByPeriod(startDate: Date, endDate: Date): LiveData<List<Transaction>> {
        return repository.getTransactionsByPeriod(startDate, endDate, defaultUserId).asLiveData()
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
}