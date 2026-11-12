package com.example.checkinoutapp.feature.organizerManagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.AppUser
import com.example.checkinoutapp.domain.model.AuditLog
import com.example.checkinoutapp.domain.model.StaffField
import com.example.checkinoutapp.domain.model.UserRole
import com.example.checkinoutapp.domain.registration.isValidEmail
import com.example.checkinoutapp.domain.repository.AuditRepository
import com.example.checkinoutapp.domain.repository.StaffOutcome
import com.example.checkinoutapp.domain.repository.UserRepository
import com.example.checkinoutapp.domain.session.SessionManager
import com.example.checkinoutapp.domain.session.SessionState
import com.example.checkinoutapp.feature.home.STOP_TIMEOUT_MILLIS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StaffFormFailure { EMAIL_IN_USE, FAILED }

data class StaffForm(
    val fullName: String = "", val email: String = "", val phone: String = "", val password: String = "", val role: UserRole = UserRole.ORGANIZER,
    val invalid: Set<StaffField> = emptySet(), val saving: Boolean = false, val failure: StaffFormFailure? = null,
)

data class StaffActivity(val user: AppUser, val logs: RequestState<List<AuditLog>>)

sealed interface OrganizersEvent {
    data object Created : OrganizersEvent
    data object Updated : OrganizersEvent
    data class Refused(val reason: String) : OrganizersEvent
    data object Failed : OrganizersEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizersViewModel(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    val staff: StateFlow<RequestState<List<AppUser>>> = userRepository.readStaffFlow(STAFF_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RequestState.Loading)

    /** An administrator cannot change their own role or switch themselves off. */
    val ownUid: StateFlow<String?> = sessionManager.state.map { (it as? SessionState.Active)?.uid }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _form = MutableStateFlow<StaffForm?>(null)
    val form: StateFlow<StaffForm?> = _form.asStateFlow()

    private val _busyUid = MutableStateFlow<String?>(null)
    val busyUid: StateFlow<String?> = _busyUid.asStateFlow()

    private val activityUser = MutableStateFlow<AppUser?>(null)
    val activity: StateFlow<StaffActivity?> = activityUser.flatMapLatest { user ->
        if (user == null) flowOf(null) else auditRepository.readAuditLogsFlow(user.uid, ACTIVITY_LIMIT).map { StaffActivity(user, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _events = Channel<OrganizersEvent>(Channel.BUFFERED)
    val events: Flow<OrganizersEvent> = _events.receiveAsFlow()

    fun openCreate() { _form.value = StaffForm() }
    fun closeForm() { if (_form.value?.saving != true) _form.value = null }
    fun edit(change: (StaffForm) -> StaffForm) = _form.update { f -> f?.takeIf { !it.saving }?.let { change(it).copy(invalid = emptySet(), failure = null) } ?: f }

    fun create() {
        val form = _form.value ?: return
        if (form.saving) return
        val invalid = buildSet {
            if (form.fullName.trim().length < 2) add(StaffField.FULL_NAME)
            if (!isValidEmail(form.email)) add(StaffField.EMAIL)
            if (form.password.length < MIN_PASSWORD_LENGTH) add(StaffField.PASSWORD)
        }
        if (invalid.isNotEmpty()) {
            _form.value = form.copy(invalid = invalid)
            return
        }
        _form.value = form.copy(saving = true)
        viewModelScope.launch {
            val result = userRepository.createStaff(
                fullName = form.fullName.trim(),
                email = form.email.trim(),
                phone = form.phone.trim(),
                password = form.password,
                role = form.role
            )
            if (result.isSuccess()) {
                when (result.getSuccessData()) {
                    StaffOutcome.SAVED -> {
                        _form.value = null
                        _events.send(OrganizersEvent.Created)
                    }
                    StaffOutcome.EMAIL_IN_USE -> _form.value = form.copy(saving = false, failure = StaffFormFailure.EMAIL_IN_USE)
                    else -> _form.value = form.copy(saving = false, failure = StaffFormFailure.FAILED)
                }
            } else {
                _form.value = form.copy(saving = false, failure = StaffFormFailure.FAILED)
            }
        }
    }

    fun setAccess(user: AppUser, role: UserRole = user.role, isActive: Boolean = user.isActive) {
        if (_busyUid.value != null) return
        _busyUid.value = user.uid
        viewModelScope.launch {
            val result = userRepository.updateAccess(uid = user.uid, role = role, isActive = isActive)
            _busyUid.value = null
            if (result.isSuccess()) {
                val outcome = result.getSuccessData()
                if (outcome == StaffOutcome.SAVED) {
                    _events.send(OrganizersEvent.Updated)
                } else {
                    _events.send(OrganizersEvent.Refused(outcome.name))
                }
            } else {
                _events.send(OrganizersEvent.Failed)
            }
        }
    }

    fun openActivity(user: AppUser) { activityUser.value = user }
    fun closeActivity() { activityUser.value = null }

    private companion object {
        const val STAFF_LIMIT = 200
        const val ACTIVITY_LIMIT = 50
        const val MIN_PASSWORD_LENGTH = 8
    }
}
