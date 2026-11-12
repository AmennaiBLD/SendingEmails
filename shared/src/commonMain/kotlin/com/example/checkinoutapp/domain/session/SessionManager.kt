package com.example.checkinoutapp.domain.session

import com.example.checkinoutapp.core.RequestState
import com.example.checkinoutapp.domain.model.AppUser
import com.example.checkinoutapp.domain.model.UserRole
import com.example.checkinoutapp.domain.repository.AuthAccount
import com.example.checkinoutapp.domain.repository.AuthRepository
import com.example.checkinoutapp.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SessionState {
    data object Unknown : SessionState
    data object SignedOut : SessionState
    data class Resolving(val account: AuthAccount) : SessionState
    data class NoAccess(val account: AuthAccount) : SessionState
    data class Active(val user: AppUser) : SessionState {
        val uid: String get() = user.uid
        val role: UserRole get() = user.role
    }
    data class Failed(val message: String) : SessionState
}


@OptIn(ExperimentalCoroutinesApi::class)
class SessionManager(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val state: StateFlow<SessionState> = authRepository.readAccountFlow()
        .distinctUntilChanged()
        .flatMapLatest { account ->
            if (account == null) flowOf(SessionState.SignedOut)
            else userRepository.readUserFlow(account.uid).map { profile -> resolve(account, profile) }
        }
        .catch { emit(SessionState.Failed(it.message.orEmpty())) }
        .stateIn(scope, SharingStarted.Eagerly, SessionState.Unknown)

    private fun resolve(account: AuthAccount, profile: RequestState<AppUser?>): SessionState = when (profile) {
        RequestState.Idle, RequestState.Loading -> SessionState.Resolving(account)
        is RequestState.Error -> SessionState.Failed(profile.message)
        is RequestState.Success -> {
            val user = profile.data
            if (user == null || !user.isActive || !user.role.canScan) SessionState.NoAccess(account) else SessionState.Active(user)
        }
    }

    suspend fun signOut() {
        authRepository.signOut()
    }
}
