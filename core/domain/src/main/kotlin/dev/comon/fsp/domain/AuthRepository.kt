package dev.comon.fsp.domain

import kotlinx.coroutines.flow.Flow

/** Online ID/PW sign-in (API-01). Passwords are passed through only; implementations must never persist them. */
interface AuthRepository {
    suspend fun login(loginId: String, password: String): LoginResult
}

sealed interface LoginResult {
    data class Success(val account: AccountSession) : LoginResult
    data class Failure(val reason: LoginFailure, val retryAfterSeconds: Long? = null) : LoginResult
}

enum class LoginFailure {
    /** No API base URL is configured for this build; no network call was made. */
    SERVER_NOT_CONFIGURED,
    /** 401 INVALID_CREDENTIALS or input the server would reject; never reveals whether the account exists. */
    INVALID_CREDENTIALS,
    /** 409: the interviewer is bound to another active device until an admin releases it. */
    ACTIVE_DEVICE_EXISTS,
    /** 403: this installation was released by an admin and cannot sign in again. */
    DEVICE_REVOKED,
    /** 429: too many attempts; see [LoginResult.Failure.retryAfterSeconds]. */
    RATE_LIMITED,
    NETWORK,
    /** 5xx or a response outside the contract. */
    SERVER_ERROR,
    /** Signed in on the server but the session could not be saved on this device. */
    STORAGE,
}

/** The signed-in account shown in the app. Display fields come from the server at sign-in. */
data class AccountSession(val userId: String, val loginId: String, val name: String, val role: Role) {
    val session: Session.Account get() = Session.Account(userId, role)
}

/** Current account session on this device (local_session + stored credentials). */
interface SessionRepository {
    /** Emits the open account session, or null once signed out or expired. */
    fun observeCurrentAccount(): Flow<AccountSession?>

    /** Restores the session at app start; null when there is none or its credentials are gone. */
    suspend fun restore(): AccountSession?

    /**
     * Ends the session. The server session is revoked when reachable, but local tokens are always
     * cleared. Unsent records of the account stay on the device.
     */
    suspend fun logout()
}
