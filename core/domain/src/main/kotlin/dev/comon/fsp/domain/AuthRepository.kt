package dev.comon.fsp.domain

/** Online ID/PW sign-in. Passwords are passed through only; implementations must never persist them. */
interface AuthRepository {
    suspend fun login(loginId: String, password: String): LoginResult
}

/** Success (session + role) is added with the real server integration in stage 2. */
sealed interface LoginResult {
    data class Failure(val reason: LoginFailure) : LoginResult
}

enum class LoginFailure {
    /** No API base URL is configured for this build; no network call was made. */
    SERVER_NOT_CONFIGURED,
    /** Same message regardless of whether the account exists. */
    INVALID_CREDENTIALS,
    NETWORK,
}
