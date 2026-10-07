package com.example.fintrack.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fintrack.ai.learn
import com.example.fintrack.data.*
import com.example.fintrack.service.Notifier
import com.example.fintrack.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.fintrack.ai.learnTx

class Vm(app: Application) : AndroidViewModel(app) {
    val dao = AppDb.get(app).dao()
    val prefs = Prefs(app)
    var themeMode by mutableIntStateOf(prefs.theme)
        private set

    private fun <T> Flow<T>.st(init: T) = stateIn(viewModelScope, SharingStarted.Eagerly, init)
    val txs = dao.txs().st(emptyList())
    val accounts = dao.accounts().st(emptyList())
    val budgets = dao.budgets().st(emptyList())
    val goals = dao.goals().st(emptyList())
    val recs = dao.recs().st(emptyList())

    fun changeTheme(m: Int) { themeMode = m; prefs.theme = m }

    fun saveTx(t: Tx, learnRule: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        val id = dao.putTx(t)
        if (t.id == 0L) Notifier.checkBudget(getApplication(), dao, t.copy(id = id))
        if (learnRule && t.merchant.isNotBlank() && t.category != "Others") learn(dao, t.merchant, t.category)
    }
    fun deleteTx(t: Tx) = viewModelScope.launch(Dispatchers.IO) { dao.delTx(t) }
    fun setCategory(t: Tx, c: String) = viewModelScope.launch(Dispatchers.IO) {
        dao.putTx(t.copy(category = c, pending = false, conf = 1f))
        learnTx(dao, t, c)
    }
    fun skipPending(t: Tx) = viewModelScope.launch(Dispatchers.IO) { dao.putTx(t.copy(pending = false)) }

    fun setBudget(cat: String, cap: Double) = viewModelScope.launch(Dispatchers.IO) {
        if (cap > 0) dao.putBudget(Budget(cat, cap)) else dao.delBudget(cat)
    }
    fun putGoal(g: Goal) = viewModelScope.launch(Dispatchers.IO) { dao.putGoal(g) }
    fun delGoal(g: Goal) = viewModelScope.launch(Dispatchers.IO) { dao.delGoal(g) }
    fun putRec(r: Recurring) = viewModelScope.launch(Dispatchers.IO) { dao.putRec(r) }
    fun delRec(r: Recurring) = viewModelScope.launch(Dispatchers.IO) { dao.delRec(r) }
    fun putAcc(a: Account) = viewModelScope.launch(Dispatchers.IO) { dao.putAcc(a) }
}
