package dev.comon.fsp.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.comon.fsp.domain.AuthRepository
import dev.comon.fsp.domain.LoginResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState(loginId = savedStateHandle[KEY_LOGIN_ID] ?: ""))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _effects = Channel<LoginEffect>(Channel.BUFFERED)
    val effects: Flow<LoginEffect> = _effects.receiveAsFlow()

    fun onIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.LoginIdChanged -> {
                savedStateHandle[KEY_LOGIN_ID] = intent.value
                _state.update { it.copy(loginId = intent.value, failure = null, retryAfterSeconds = null) }
            }
            is LoginIntent.PasswordChanged ->
                _state.update { it.copy(password = intent.value, failure = null, retryAfterSeconds = null) }
            LoginIntent.Submit -> submit()
            LoginIntent.EnterOfflineMode -> {
                // Offline mode performs no API call or authentication.
                _state.update { it.copy(password = "", failure = null, retryAfterSeconds = null) }
                _effects.trySend(LoginEffect.OpenOfflineMode)
            }
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(submitting = true, failure = null, retryAfterSeconds = null) }
        viewModelScope.launch {
            // Password is passed as typed: no trim or case change.
            when (val result = authRepository.login(current.loginId.trim(), current.password)) {
                is LoginResult.Success -> {
                    _state.update { it.copy(submitting = false, password = "") }
                    _effects.send(LoginEffect.SignedIn)
                }
                is LoginResult.Failure -> _state.update {
                    it.copy(submitting = false, password = "", failure = result.reason, retryAfterSeconds = result.retryAfterSeconds)
                }
            }
        }
    }

    private companion object {
        const val KEY_LOGIN_ID = "loginId"
    }
}
