package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Account::class, Category::class, Transaction::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
}
