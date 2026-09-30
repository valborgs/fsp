package dev.comon.fsp.feature.auth

import dev.comon.fsp.domain.LoginFailure

data class LoginUiState(
    val loginId: String = "",
    /** Held in ViewModel memory only; never written to SavedStateHandle or the back stack. */
    val password: String = "",
    val submitting: Boolean = false,
    val failure: LoginFailure? = null,
) {
    val canSubmit: Boolean get() = loginId.isNotBlank() && password.isNotEmpty() && !submitting

    override fun toString(): String =
        "LoginUiState(loginId=$loginId, password=***, submitting=$submitting, failure=$failure)"
}

sealed interface LoginIntent {
    data class LoginIdChanged(val value: String) : LoginIntent
    data class PasswordChanged(val value: String) : LoginIntent {
        override fun toString(): String = "PasswordChanged(***)"
    }
    data object Submit : LoginIntent
    data object EnterOfflineMode : LoginIntent
}

sealed interface LoginEffect {
    data object OpenOfflineMode : LoginEffect
}
