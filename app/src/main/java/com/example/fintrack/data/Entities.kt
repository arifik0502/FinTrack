package com.example.fintrack.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Cats {
    val all = listOf("Food", "Transport", "Shopping", "Bills", "Entertainment", "Health", "Education", "Others")
}

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String, // CASH, BANK, EWALLET
    val initial: Double = 0.0
)

@Entity(tableName = "tx", indices = [Index("ts"), Index("dedup"), Index("hash")])
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: String = "EXPENSE", // EXPENSE, INCOME
    val merchant: String = "",
    val category: String = "Others",
    val accountId: Long = 1,
    val ts: Long,
    val note: String = "",
    val source: String = "MANUAL", // MANUAL, AUTO, RECEIPT, RECURRING
    val app: String = "",
    val pending: Boolean = false, // needs user category choice
    val conf: Float = 1f,
    val dedup: String = "",
    val hash: String = "", // sha of notification, never raw text
    val items: String = ""
)

@Entity(tableName = "budgets")
data class Budget(@PrimaryKey val cat: String, val cap: Double) // cat = "ALL" or category

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Double,
    val saved: Double = 0.0
)

@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val category: String = "Bills",
    val accountId: Long = 1,
    val day: Int = 1,
    val lastRun: String = "",
    val active: Boolean = true,
    val type: String = "EXPENSE"
)

@Entity(tableName = "rules")
data class Rule(@PrimaryKey val merchant: String, val category: String)
