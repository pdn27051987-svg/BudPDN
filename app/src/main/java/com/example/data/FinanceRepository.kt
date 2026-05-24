package com.example.data

import android.content.Context
import com.example.data.api.NbrbClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FinanceRepository(private val db: AppDatabase) {
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()

    val allAccountsFlow: Flow<List<Account>> = accountDao.getAllAccountsFlow()
    val allCategoriesFlow: Flow<List<Category>> = categoryDao.getAllCategoriesFlow()
    val allTransactionsFlow: Flow<List<Transaction>> = transactionDao.getAllTransactionsFlow()

    suspend fun getAllAccounts(): List<Account> = accountDao.getAllAccounts()
    suspend fun getAccountById(id: Long): Account? = accountDao.getAccountById(id)
    suspend fun insertAccount(account: Account): Long = accountDao.insertAccount(account)
    suspend fun updateAccount(account: Account) = accountDao.updateAccount(account)
    suspend fun deleteAccount(account: Account) = accountDao.deleteAccount(account)

    suspend fun getAllCategories(): List<Category> = categoryDao.getAllCategories()
    suspend fun insertCategory(category: Category): Long = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)

    suspend fun getTransactionById(id: Long): Transaction? = transactionDao.getTransactionById(id)
    suspend fun insertTransaction(transaction: Transaction): Long = transactionDao.insertTransaction(transaction)
    suspend fun updateTransaction(transaction: Transaction) = transactionDao.updateTransaction(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = transactionDao.deleteTransaction(transaction)

    suspend fun initializeDefaultCategoriesIfEmpty() {
        val count = categoryDao.getAllCategories().size
        if (count == 0) {
            val defaults = listOf(
                // Expenses
                Category(name = "Продукты", type = "EXPENSE", iconName = "shopping_cart", isDefault = true),
                Category(name = "Транспорт", type = "EXPENSE", iconName = "directions_car", isDefault = true),
                Category(name = "Жилье", type = "EXPENSE", iconName = "home", isDefault = true),
                Category(name = "Развлечения", type = "EXPENSE", iconName = "sports_esports", isDefault = true),
                Category(name = "Здоровье", type = "EXPENSE", iconName = "medical_services", isDefault = true),
                Category(name = "Кафе и рестораны", type = "EXPENSE", iconName = "restaurant", isDefault = true),
                Category(name = "Одежда", type = "EXPENSE", iconName = "checkroom", isDefault = true),
                Category(name = "Образование", type = "EXPENSE", iconName = "school", isDefault = true),
                Category(name = "Другое", type = "EXPENSE", iconName = "category", isDefault = true),

                // Incomes
                Category(name = "Зарплата", type = "INCOME", iconName = "payments", isDefault = true),
                Category(name = "Бизнес", type = "INCOME", iconName = "business_center", isDefault = true),
                Category(name = "Подарки", type = "INCOME", iconName = "redeem", isDefault = true),
                Category(name = "Инвестиции", type = "INCOME", iconName = "trending_up", isDefault = true),
                Category(name = "Другое", type = "INCOME", iconName = "monetization_on", isDefault = true)
            )
            categoryDao.insertCategories(defaults)
        }
    }

    suspend fun initializeDefaultAccountsIfEmpty() {
        val count = accountDao.getAllAccounts().size
        if (count == 0) {
            val defaults = listOf(
                Account(name = "Карта BYN", currency = "BYN", initialBalance = 500.0, isActive = true),
                Account(name = "Наличные (USD)", currency = "USD", initialBalance = 100.0, isActive = true),
                Account(name = "Сберкарта RUB", currency = "RUB", initialBalance = 5000.0, isActive = true)
            )
            for (acc in defaults) {
                accountDao.insertAccount(acc)
            }
        }
    }

    suspend fun fetchRatesFor(currencies: List<String>): Map<String, Double> {
        val rates = mutableMapOf<String, Double>()
        rates["BYN"] = 1.0
        for (cur in currencies) {
            if (cur != "BYN") {
                rates[cur] = NbrbClient.getExchangeRateInByn(cur)
            }
        }
        return rates
    }

    suspend fun restoreAllData(accountsList: List<Account>, categoriesList: List<Category>, transactionsList: List<Transaction>) {
        accountDao.deleteAllAccounts()
        categoryDao.deleteAllCategories()
        transactionDao.deleteAllTransactions()

        accountDao.insertAccounts(accountsList)
        categoryDao.insertCategories(categoriesList)
        transactionDao.insertTransactions(transactionsList)
    }
}
