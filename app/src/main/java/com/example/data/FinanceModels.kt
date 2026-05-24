package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currency: String, // "BYN", "USD", "EUR", "RUB"
    val initialBalance: Double = 0.0,
    val isActive: Boolean = true
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "EXPENSE", "INCOME"
    val iconName: String, // Icon identifier tag
    val isDefault: Boolean = false
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val accountId: Long, // Source account
    val toAccountId: Long? = null, // Destination account (only if type == "TRANSFER")
    val type: String, // "EXPENSE", "INCOME", "TRANSFER"
    val categoryId: Long? = null, // Category reference (null for transfers)
    val date: String, // Format: "yyyy-MM-dd"
    val description: String = "",
    val recurrence: String = "NONE", // "NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY", "CUSTOM"
    val recurrencePeriod: String = "NONE", // "NONE", "DAY", "WEEK", "MONTH", "YEAR"
    val recurrenceCount: Int = 1,
    val transferRate: Double? = null, // Exchange rate for cross-currency transfers (e.g., target / source)
    val isPlanned: Boolean = false // If true, it's a future planned transaction
)
