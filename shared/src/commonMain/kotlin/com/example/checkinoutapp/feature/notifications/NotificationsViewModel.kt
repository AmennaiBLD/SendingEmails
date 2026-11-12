package com.example.checkinoutapp.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.NotificationJob
import com.example.checkinoutapp.domain.model.NotificationStatus
import com.example.checkinoutapp.domain.repository.NotificationRepository
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NotificationsEvent {
    data class Retried(val status: NotificationStatus) : NotificationsEvent
    data object Failed : NotificationsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel(private val notificationRepository: NotificationRepository) : ViewModel() {

    /** null = every status. */
    private val _filter = MutableStateFlow<NotificationStatus?>(null)
    val filter: StateFlow<NotificationStatus?> = _filter.asStateFlow()

    val jobs: StateFlow<RequestState<List<NotificationJob>>> = _filter
        .flatMapLatest { notificationRepository.readNotificationsFlow(it, JOB_LIMIT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    private val _retrying = MutableStateFlow<String?>(null)
    val retrying: StateFlow<String?> = _retrying.asStateFlow()

    private val _detail = MutableStateFlow<NotificationJob?>(null)
    val detail: StateFlow<NotificationJob?> = _detail.asStateFlow()

    private val _events = Channel<NotificationsEvent>(Channel.BUFFERED)
    val events: Flow<NotificationsEvent> = _events.receiveAsFlow()

    fun setFilter(status: NotificationStatus?) { _filter.value = status }
    fun openDetail(job: NotificationJob) { _detail.value = job }
    fun closeDetail() { _detail.value = null }

    fun retry(job: NotificationJob) {
        if (_retrying.value != null) return
        _retrying.value = job.id
        viewModelScope.launch {
            val result = notificationRepository.retryNotification(jobId = job.id)
            _retrying.value = null
            _detail.value = null
            if (result.isSuccess()) {
                _events.send(NotificationsEvent.Retried(result.getSuccessData()))
            } else {
                _events.send(NotificationsEvent.Failed)
            }
        }
    }

    private companion object {
        const val JOB_LIMIT = 200
    }
}
