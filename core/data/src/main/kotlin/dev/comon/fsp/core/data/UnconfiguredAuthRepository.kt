package dev.comon.fsp.core.data

import dev.comon.fsp.domain.AuthRepository
import dev.comon.fsp.domain.LoginFailure
import dev.comon.fsp.domain.LoginResult
import javax.inject.Inject

/**
 * Bound until the server base URL and the Retrofit client exist (1B-3 / stage 2).
 * Reports a real failure instead of pretending to sign in, and never touches the password.
 */
class UnconfiguredAuthRepository @Inject constructor() : AuthRepository {
    override suspend fun login(loginId: String, password: String): LoginResult =
        LoginResult.Failure(LoginFailure.SERVER_NOT_CONFIGURED)
}
