package `in`.dattam.smsforwarder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.dattam.smsforwarder.data.AppDatabase
import `in`.dattam.smsforwarder.data.ForwardingRule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).ruleDao()
    val rules = dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun upsert(rule: ForwardingRule) = viewModelScope.launch { dao.upsert(rule) }
    fun delete(rule: ForwardingRule) = viewModelScope.launch { dao.delete(rule) }
    fun toggle(rule: ForwardingRule) = viewModelScope.launch {
        dao.update(rule.copy(enabled = !rule.enabled))
    }
}
