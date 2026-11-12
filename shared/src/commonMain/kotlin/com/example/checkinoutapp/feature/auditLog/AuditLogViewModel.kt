package com.example.checkinoutapp.feature.auditLog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.AuditLog
import com.example.checkinoutapp.domain.repository.AuditRepository
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AuditLogViewModel(auditRepository: AuditRepository) : ViewModel() {

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    /** The most recent entries, filtered by actor, action or reason. The log itself is append-only. */
    val logs: StateFlow<RequestState<List<AuditLog>>> = combine(auditRepository.readAuditLogsFlow(null, LOG_LIMIT), _search) { state, search ->
        val needle = search.trim().lowercase()
        state.map { logs ->
            if (needle.isEmpty()) logs
            else logs.filter { it.actorName.lowercase().contains(needle) || it.action.lowercase().replace('_', ' ').contains(needle) || it.reason.orEmpty().lowercase().contains(needle) || it.details.lowercase().contains(needle) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    fun setSearch(text: String) { _search.value = text.take(80) }

    private companion object {
        const val LOG_LIMIT = 300
    }
}
