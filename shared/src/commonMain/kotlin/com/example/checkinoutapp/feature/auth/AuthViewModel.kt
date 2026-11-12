package com.example.checkinoutapp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.checkinoutapp.domain.registration.isValidEmail
import com.example.checkinoutapp.domain.repository.AuthRepository
import com.example.checkinoutapp.domain.session.SessionManager
import com.example.checkinoutapp.domain.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInError { INVALID_EMAIL, PASSWORD_REQUIRED, FAILED }

data class SignInForm(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: SignInError? = null,
    val message: String = "",
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val session: StateFlow<SessionState> = sessionManager.state

    private val _form = MutableStateFlow(SignInForm())
    val form: StateFlow<SignInForm> = _form.asStateFlow()

    fun setEmail(value: String) = _form.update { if (it.busy) it else it.copy(email = value.take(254), error = null) }
    fun setPassword(value: String) = _form.update { if (it.busy) it else it.copy(password = value.take(128), error = null) }

    fun signIn() {
        val current = _form.value
        if (current.busy) return
        if (!isValidEmail(current.email)) {
            _form.value = current.copy(error = SignInError.INVALID_EMAIL)
            return
        }
        if (current.password.isEmpty()) {
            _form.value = current.copy(error = SignInError.PASSWORD_REQUIRED)
            return
        }
        _form.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = authRepository.signIn(email = current.email.trim(), password = current.password.lowercase())
            if (result.isSuccess()) {
                _form.value = SignInForm()
            } else if (result.isError()) {
                // The password is cleared after a failed attempt; the email stays.
                _form.value = current.copy(busy = false, password = "", error = SignInError.FAILED, message = result.getErrorMessage())
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { sessionManager.signOut() }
    }
}
