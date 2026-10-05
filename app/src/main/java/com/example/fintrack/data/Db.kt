package com.example.fintrack.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTx(t: Tx): Long
    @Delete suspend fun delTx(t: Tx)
    @Query("SELECT * FROM tx ORDER BY ts DESC") fun txs(): Flow<List<Tx>>
    @Query("SELECT * FROM tx ORDER BY ts DESC") suspend fun txsNow(): List<Tx>
    @Query("SELECT * FROM tx WHERE id=:id") suspend fun tx(id: Long): Tx?
    @Query("SELECT COUNT(*) FROM tx WHERE hash=:h AND ts>:since") suspend fun hashDup(h: String, since: Long): Int
    @Query("SELECT COUNT(*) FROM tx WHERE dedup=:k AND ts BETWEEN :a AND :b") suspend fun amtDup(k: String, a: Long, b: Long): Int
    @Query("SELECT COALESCE(SUM(amount),0) FROM tx WHERE type='EXPENSE' AND ts BETWEEN :a AND :b AND (:cat='ALL' OR category=:cat)")
    suspend fun spent(a: Long, b: Long, cat: String): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAcc(a: Account): Long
    @Delete suspend fun delAcc(a: Account)
    @Query("SELECT * FROM accounts") fun accounts(): Flow<List<Account>>
    @Query("SELECT * FROM accounts") suspend fun accountsNow(): List<Account>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putBudget(b: Budget)
    @Query("DELETE FROM budgets WHERE cat=:k") suspend fun delBudget(k: String)
    @Query("SELECT * FROM budgets") fun budgets(): Flow<List<Budget>>
    @Query("SELECT * FROM budgets") suspend fun budgetsNow(): List<Budget>
    @Query("SELECT * FROM budgets WHERE cat=:k") suspend fun budgetNow(k: String): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putGoal(g: Goal)
    @Delete suspend fun delGoal(g: Goal)
    @Query("SELECT * FROM goals") fun goals(): Flow<List<Goal>>
    @Query("SELECT * FROM goals") suspend fun goalsNow(): List<Goal>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRec(r: Recurring)
    @Delete suspend fun delRec(r: Recurring)
    @Query("SELECT * FROM recurring") fun recs(): Flow<List<Recurring>>
    @Query("SELECT * FROM recurring") suspend fun recsNow(): List<Recurring>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRule(r: Rule)
    @Query("SELECT * FROM rules") suspend fun rulesNow(): List<Rule>
}

@Database(
    entities = [Tx::class, Account::class, Budget::class, Goal::class, Recurring::class, Rule::class],
    version = 1, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "fintrack.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO accounts(name,kind,initial) VALUES ('Cash','CASH',0),('Bank','BANK',0),('E-Wallet','EWALLET',0)")
                    }
                }).build().also { inst = it }
        }
    }
}
