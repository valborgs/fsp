package dev.comon.fsp.core.data

import dev.comon.fsp.core.database.entity.LocalSessionEntity
import dev.comon.fsp.core.database.entity.SessionMode
import dev.comon.fsp.core.network.ApiClient
import dev.comon.fsp.core.network.ApiFailure
import dev.comon.fsp.core.network.ApiResult
import dev.comon.fsp.core.network.DeviceIdProvider
import dev.comon.fsp.core.network.auth.AuthApi
import dev.comon.fsp.core.network.auth.LoginRequest
import dev.comon.fsp.core.network.auth.LoginResultDto
import dev.comon.fsp.domain.AccountSession
import dev.comon.fsp.domain.AuthRepository
import dev.comon.fsp.domain.CredentialRules
import dev.comon.fsp.domain.LoginFailure
import dev.comon.fsp.domain.LoginResult
import dev.comon.fsp.domain.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * API-01 sign-in. Input that the server would reject is answered locally with the same generic
 * failure, so no request reveals anything and the rate limit is not consumed. On success the tokens
 * (encrypted refresh token, in-memory access token) and the account session row are stored; if
 * either cannot be saved, nothing is kept and the user sees a storage failure.
 */
class NetworkAuthRepository @Inject constructor(
    private val api: ApiClient,
    private val deviceIds: DeviceIdProvider,
    private val credentials: SessionCredentials,
    private val sessions: AccountSessionStore,
    private val clock: Clock,
) : AuthRepository {
    override suspend fun login(loginId: String, password: String): LoginResult {
        val id = loginId.trim()
        if (!CredentialRules.isValidLoginId(id) || !CredentialRules.isValidPassword(password)) {
            return LoginResult.Failure(LoginFailure.INVALID_CREDENTIALS)
        }
        val deviceId = deviceIds.deviceId()
        return when (val result = api.call(AuthApi::class) { login(LoginRequest(id, password, deviceId)) }) {
            is ApiResult.Success -> signIn(result.data, deviceId)
            is ApiResult.Failure -> result.failure.toLoginFailure()
        }
    }

    private suspend fun signIn(result: LoginResultDto, deviceId: String): LoginResult {
        val user = result.user
        val role = Role.fromGrade(user.grade)?.takeIf { it.name == user.role }
        if (role == null || result.tokens.tokenType != "Bearer") return LoginResult.Failure(LoginFailure.SERVER_ERROR)
        val loginId = CredentialRules.normalizeLoginId(user.id)
        val now = clock.millis()
        try {
            withContext(Dispatchers.IO) { credentials.store(user.userId, result.tokens.accessToken, result.tokens.refreshToken) }
            sessions.start(
                LocalSessionEntity(
                    sessionId = UUID.randomUUID().toString(),
                    mode = SessionMode.ACCOUNT,
                    userId = user.userId,
                    deviceId = deviceId,
                    roleGrade = user.grade,
                    createdAt = now,
                    loginId = loginId,
                    displayName = user.name,
                    serverSessionId = result.tokens.sessionId,
                    deviceNextSequence = result.deviceNextSequence,
                ),
            )
        } catch (e: CancellationException) {
            credentials.clear()
            throw e
        } catch (_: Exception) {
            credentials.clear()
            return LoginResult.Failure(LoginFailure.STORAGE)
        }
        return LoginResult.Success(AccountSession(user.userId, loginId, user.name, role))
    }
}

/** v1.4 §13 codes first, then HTTP status. 422 means input the server rejects: same generic message. */
internal fun ApiFailure.toLoginFailure(): LoginResult.Failure = when (this) {
    ApiFailure.NotConfigured -> LoginResult.Failure(LoginFailure.SERVER_NOT_CONFIGURED)
    is ApiFailure.Network -> LoginResult.Failure(LoginFailure.NETWORK)
    ApiFailure.MalformedResponse -> LoginResult.Failure(LoginFailure.SERVER_ERROR)
    is ApiFailure.Http -> when {
        code == "ACTIVE_DEVICE_EXISTS" -> LoginResult.Failure(LoginFailure.ACTIVE_DEVICE_EXISTS)
        code == "DEVICE_REVOKED" -> LoginResult.Failure(LoginFailure.DEVICE_REVOKED)
        status == 429 -> LoginResult.Failure(LoginFailure.RATE_LIMITED, retryAfterMillis?.let { (it + 999) / 1_000 })
        status == 401 || status == 422 -> LoginResult.Failure(LoginFailure.INVALID_CREDENTIALS)
        else -> LoginResult.Failure(LoginFailure.SERVER_ERROR)
    }
}
