package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.*
import com.example.data.api.NbrbClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "FinanceViewModel"

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "personal_finance.db"
    ).fallbackToDestructiveMigration().build()

    private val repository = FinanceRepository(db)

    // Language and Theme Preferences (with fast in-memory StateFlow)
    val appLanguage = MutableStateFlow(Localization.Language.RU)
    val appTheme = MutableStateFlow("SYSTEM") // "LIGHT", "DARK", "SYSTEM"

    // Base currency for calendar calculations (BYN is standard)
    val baseCurrency = MutableStateFlow("BYN")
    val financialPeriodStartDate = MutableStateFlow("1") // Format: "DD" (Day of Month)

    // UI state flows from local DB
    val accounts = repository.allAccountsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val categories = repository.allCategoriesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val transactions = repository.allTransactionsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // NBRB rates cache
    private val _nbrbRates = MutableStateFlow<Map<String, Double>>(
        mapOf("BYN" to 1.0, "USD" to 3.25, "EUR" to 3.55, "RUB" to 3.42 / 100.0)
    )
    val nbrbRates = _nbrbRates.asStateFlow()

    // Interactive UI state
    val currentTab = MutableStateFlow(0) // 0: Calendar, 1: Transactions, 2: Analytics, 3: Settings
    val selectedDate = MutableStateFlow("") // Format: "yyyy-MM-dd"
    val calendarYearMonth = MutableStateFlow("") // Format: "yyyy-MM"

    // Filters for Tab 2 (Transactions list)
    val searchQuery = MutableStateFlow("")
    val filterAccountId = MutableStateFlow<Long?>(null)
    val filterCategoryId = MutableStateFlow<Long?>(null)
    val filterType = MutableStateFlow<String?>(null) // "INCOME", "EXPENSE", "TRANSFER", null: ALL

    // Analytics Filters Tab 3
    val analyticsPeriodType = MutableStateFlow("MONTH") // "DAY", "MONTH", "YEAR", "ALL"
    val analyticsSelectedDate = MutableStateFlow("") // e.g., "2026-05" for MONTH, "2026-05-23" for DAY

    init {
        val today = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date())
        selectedDate.value = today
        calendarYearMonth.value = today.substring(0, 7)
        analyticsSelectedDate.value = today.substring(0, 7)

        viewModelScope.launch {
            repository.initializeDefaultCategoriesIfEmpty()
            repository.initializeDefaultAccountsIfEmpty()
            refreshNbrbRates()
        }
    }

    fun refreshNbrbRates() {
        viewModelScope.launch {
            try {
                val fetched = repository.fetchRatesFor(listOf("USD", "EUR", "RUB"))
                val newMap = _nbrbRates.value.toMutableMap()
                newMap.putAll(fetched)
                _nbrbRates.value = newMap
                Log.d(TAG, "Refreshed official NBRB rates: $newMap")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh rates: ${e.message}")
            }
        }
    }

    // --- Helper projections to handle RECURRING/PLANNED transactions ---

    // Parses a "yyyy-MM-dd" date string into calendar parts
    private fun parseDateStr(dateStr: String): Calendar? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val cal = Calendar.getInstance()
            cal.time = sdf.parse(dateStr) ?: return null
            cal
        } catch (e: Exception) {
            null
        }
    }

    // Formats a Calendar instance back to "yyyy-MM-dd"
    private fun formatCalendar(cal: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(cal.time)
    }

    // Projects if a transaction occurs on checkingDate
    private fun isTransactionOccurringOn(tx: Transaction, checkingDateStr: String): Boolean {
        if (tx.date == checkingDateStr) return true
        if (checkingDateStr < tx.date) return false

        val effPeriod = if (tx.recurrencePeriod != "NONE") tx.recurrencePeriod else {
            when (tx.recurrence) {
                "DAILY" -> "DAY"
                "WEEKLY" -> "WEEK"
                "MONTHLY" -> "MONTH"
                "YEARLY" -> "YEAR"
                else -> "NONE"
            }
        }
        if (effPeriod == "NONE") return false

        val start = parseDateStr(tx.date) ?: return false
        val check = parseDateStr(checkingDateStr) ?: return false

        val diffMills = check.timeInMillis - start.timeInMillis
        val diffDays = diffMills / (1000 * 60 * 60 * 24)

        if (tx.recurrenceCount <= 1) {
            when (effPeriod) {
                "DAY" -> return true
                "WEEK" -> return diffDays % 7 == 0L
                "MONTH" -> {
                    return check.get(Calendar.DAY_OF_MONTH) == start.get(Calendar.DAY_OF_MONTH) ||
                            (start.get(Calendar.DAY_OF_MONTH) > check.getActualMaximum(Calendar.DAY_OF_MONTH) &&
                                    check.get(Calendar.DAY_OF_MONTH) == check.getActualMaximum(Calendar.DAY_OF_MONTH))
                }
                "YEAR" -> {
                    return check.get(Calendar.MONTH) == start.get(Calendar.MONTH) &&
                            check.get(Calendar.DAY_OF_MONTH) == start.get(Calendar.DAY_OF_MONTH)
                }
            }
        }

        val daysStep = when (effPeriod) {
            "DAY" -> 1.0 / tx.recurrenceCount
            "WEEK" -> 7.0 / tx.recurrenceCount
            "MONTH" -> 30.0 / tx.recurrenceCount
            "YEAR" -> 365.0 / tx.recurrenceCount
            else -> return false
        }

        val index = Math.round(diffDays / daysStep)
        val checkDays = Math.round(index * daysStep)
        return diffDays == checkDays
    }

    // Returns virtual projected instances of transactions on a particular day
    fun getTransactionsForDate(dateStr: String, list: List<Transaction>): List<Transaction> {
        return list.filter { isTransactionOccurringOn(it, dateStr) }.map {
            if (it.date != dateStr) {
                // Return a virtual copy projected on this date
                it.copy(date = dateStr, isPlanned = dateStr > SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(Date()))
            } else {
                it
            }
        }
    }

    // Dynamic accounts with balances computed up to a specific date
    fun getAccountBalancesOnDate(dateStr: String, allAccs: List<Account>, allTxs: List<Transaction>): List<Pair<Account, Double>> {
        val rates = _nbrbRates.value
        val result = mutableListOf<Pair<Account, Double>>()

        for (acc in allAccs) {
            var balance = acc.initialBalance

            // Gather all transactions up to this date on this account
            val relevantTxs = allTxs.filter { it.date <= dateStr }
            for (tx in relevantTxs) {
                // If it's a recurring expense, we project all its occurrences up to dateStr
                val start = parseDateStr(tx.date) ?: continue
                val maxCheck = parseDateStr(dateStr) ?: continue

                val effPeriod = if (tx.recurrencePeriod != "NONE") tx.recurrencePeriod else {
                    when (tx.recurrence) {
                        "DAILY" -> "DAY"
                        "WEEKLY" -> "WEEK"
                        "MONTHLY" -> "MONTH"
                        "YEARLY" -> "YEAR"
                        else -> "NONE"
                    }
                }

                if (effPeriod == "NONE") {
                    if (tx.accountId == acc.id) {
                        if (tx.type == "EXPENSE" || tx.type == "TRANSFER") {
                            balance -= tx.amount
                        } else if (tx.type == "INCOME") {
                            balance += tx.amount
                        }
                    }
                    if (tx.toAccountId == acc.id && tx.type == "TRANSFER") {
                        val rate = tx.transferRate ?: 1.0
                        balance += tx.amount * rate
                    }
                } else {
                    // Recurring transactions project multiple values
                    val tempCal = start.clone() as Calendar
                    while (formatCalendar(tempCal) <= dateStr) {
                        val currentProjectedDate = formatCalendar(tempCal)
                        if (isTransactionOccurringOn(tx, currentProjectedDate)) {
                            if (tx.accountId == acc.id) {
                                if (tx.type == "EXPENSE" || tx.type == "TRANSFER") {
                                    balance -= tx.amount
                                } else if (tx.type == "INCOME") {
                                    balance += tx.amount
                                }
                            }
                            if (tx.toAccountId == acc.id && tx.type == "TRANSFER") {
                                val rate = tx.transferRate ?: 1.0
                                balance += tx.amount * rate
                            }
                        }
                        tempCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }
            result.add(acc to balance)
        }
        return result
    }

    // Helper: Total overall net worth in baseCurrency for checkingDate
    fun getTotalActiveBalanceOnDate(dateStr: String, allAccs: List<Account>, allTxs: List<Transaction>): Double {
        val accountBalances = getAccountBalancesOnDate(dateStr, allAccs, allTxs)
        val rates = _nbrbRates.value
        val baseCur = baseCurrency.value

        val baseToBynRate = rates[baseCur] ?: 1.0

        var totalByn = 0.0
        for ((acc, bal) in accountBalances) {
            if (acc.isActive) {
                val accCurRateInByn = rates[acc.currency] ?: 1.0
                val balInByn = bal * accCurRateInByn
                totalByn += balInByn
            }
        }

        // Convert totalByn to baseCurrency
        return totalByn / baseToBynRate
    }

    // --- Database Operations ---

    fun addAccount(name: String, currency: String, initialBalance: Double) {
        viewModelScope.launch {
            repository.insertAccount(Account(name = name, currency = currency, initialBalance = initialBalance, isActive = true))
        }
    }

    fun toggleAccountActive(account: Account) {
        viewModelScope.launch {
            repository.updateAccount(account.copy(isActive = !account.isActive))
        }
    }

    fun deleteAccount(account: Account) {
        viewModelScope.launch {
            repository.deleteAccount(account)
        }
    }

    fun addCategory(name: String, type: String, iconName: String) {
        viewModelScope.launch {
            repository.insertCategory(Category(name = name, type = type, iconName = iconName))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    fun addTransaction(
        amount: Double,
        accountId: Long,
        toAccountId: Long? = null,
        type: String,
        categoryId: Long? = null,
        date: String,
        description: String = "",
        recurrence: String = "NONE",
        recurrencePeriod: String = "NONE",
        recurrenceCount: Int = 1,
        transferRate: Double? = null,
        isPlanned: Boolean = false
    ) {
        viewModelScope.launch {
            repository.insertTransaction(
                Transaction(
                    amount = amount,
                    accountId = accountId,
                    toAccountId = toAccountId,
                    type = type,
                    categoryId = categoryId,
                    date = date,
                    description = description,
                    recurrence = recurrence,
                    recurrencePeriod = recurrencePeriod,
                    recurrenceCount = recurrenceCount,
                    transferRate = transferRate,
                    isPlanned = isPlanned
                )
            )
        }
    }

    // --- Backup and Restore Operations ---

    fun exportBackupJson(): String {
        val obj = org.json.JSONObject()
        try {
            obj.put("version", 1)
            
            val accsArray = org.json.JSONArray()
            for (acc in accounts.value) {
                val a = org.json.JSONObject()
                a.put("id", acc.id)
                a.put("name", acc.name)
                a.put("currency", acc.currency)
                a.put("initialBalance", acc.initialBalance)
                a.put("isActive", acc.isActive)
                accsArray.put(a)
            }
            obj.put("accounts", accsArray)
            
            val catsArray = org.json.JSONArray()
            for (cat in categories.value) {
                val c = org.json.JSONObject()
                c.put("id", cat.id)
                c.put("name", cat.name)
                c.put("type", cat.type)
                c.put("iconName", cat.iconName)
                c.put("isDefault", cat.isDefault)
                catsArray.put(c)
            }
            obj.put("categories", catsArray)
            
            val txsArray = org.json.JSONArray()
            for (tx in transactions.value) {
                val t = org.json.JSONObject()
                t.put("id", tx.id)
                t.put("amount", tx.amount)
                t.put("accountId", tx.accountId)
                t.put("toAccountId", tx.toAccountId ?: org.json.JSONObject.NULL)
                t.put("type", tx.type)
                t.put("categoryId", tx.categoryId ?: org.json.JSONObject.NULL)
                t.put("date", tx.date)
                t.put("description", tx.description)
                t.put("recurrence", tx.recurrence)
                t.put("recurrencePeriod", tx.recurrencePeriod)
                t.put("recurrenceCount", tx.recurrenceCount)
                t.put("transferRate", tx.transferRate ?: org.json.JSONObject.NULL)
                t.put("isPlanned", tx.isPlanned)
                txsArray.put(t)
            }
            obj.put("transactions", txsArray)
            
            val settingsObj = org.json.JSONObject()
            settingsObj.put("baseCurrency", baseCurrency.value)
            settingsObj.put("financialPeriodStartDate", financialPeriodStartDate.value)
            obj.put("settings", settingsObj)
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating backup JSON", e)
        }
        return obj.toString(2)
    }

    fun restoreBackupJson(jsonStr: String): Boolean {
        return try {
            val obj = org.json.JSONObject(jsonStr)
            
            val accList = mutableListOf<Account>()
            val accsArray = obj.getJSONArray("accounts")
            for (i in 0 until accsArray.length()) {
                val a = accsArray.getJSONObject(i)
                accList.add(Account(
                    id = a.getLong("id"),
                    name = a.getString("name"),
                    currency = a.getString("currency"),
                    initialBalance = a.getDouble("initialBalance"),
                    isActive = a.optBoolean("isActive", true)
                ))
            }
            
            val catList = mutableListOf<Category>()
            val catsArray = obj.getJSONArray("categories")
            for (i in 0 until catsArray.length()) {
                val c = catsArray.getJSONObject(i)
                catList.add(Category(
                    id = c.getLong("id"),
                    name = c.getString("name"),
                    type = c.getString("type"),
                    iconName = c.getString("iconName"),
                    isDefault = c.optBoolean("isDefault", false)
                ))
            }
            
            val txList = mutableListOf<Transaction>()
            val txsArray = obj.getJSONArray("transactions")
            for (i in 0 until txsArray.length()) {
                val t = txsArray.getJSONObject(i)
                txList.add(Transaction(
                    id = t.getLong("id"),
                    amount = t.getDouble("amount"),
                    accountId = t.getLong("accountId"),
                    toAccountId = if (t.isNull("toAccountId")) null else t.getLong("toAccountId"),
                    type = t.getString("type"),
                    categoryId = if (t.isNull("categoryId")) null else t.getLong("categoryId"),
                    date = t.getString("date"),
                    description = t.optString("description", ""),
                    recurrence = t.optString("recurrence", "NONE"),
                    recurrencePeriod = t.optString("recurrencePeriod", "NONE"),
                    recurrenceCount = t.optInt("recurrenceCount", 1),
                    transferRate = if (t.isNull("transferRate")) null else t.getDouble("transferRate"),
                    isPlanned = t.optBoolean("isPlanned", false)
                ))
            }
            
            val settingsObj = obj.optJSONObject("settings")
            val restoredBaseCurrency = settingsObj?.optString("baseCurrency", "BYN") ?: "BYN"
            val restoredStartDate = settingsObj?.optString("financialPeriodStartDate", "") ?: ""
            
            viewModelScope.launch {
                repository.restoreAllData(accList, catList, txList)
                baseCurrency.value = restoredBaseCurrency
                financialPeriodStartDate.value = restoredStartDate
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}", e)
            false
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    // Returns the conversion rate between currency A and currency B according to NBRB API
    // e.g., if converting from USD to BYN, it returns 3.25.
    // if converting from BYN to USD, it returns 1.0 / 3.25.
    // if converting from USD to EUR, it returns USD_rate_in_BYN / EUR_rate_in_BYN.
    fun getCrossRate(fromCur: String, toCur: String): Double {
        val rates = _nbrbRates.value
        val fromRate = rates[fromCur] ?: 1.0
        val toRate = rates[toCur] ?: 1.0
        return fromRate / toRate
    }
}
