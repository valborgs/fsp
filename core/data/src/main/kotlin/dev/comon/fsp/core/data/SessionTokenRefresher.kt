package dev.comon.fsp.core.data

import dev.comon.fsp.core.network.ApiClient
import dev.comon.fsp.core.network.ApiFailure
import dev.comon.fsp.core.network.ApiResult
import dev.comon.fsp.core.network.DeviceIdProvider
import dev.comon.fsp.core.network.TokenRefresher
import dev.comon.fsp.core.network.auth.AuthApi
import dev.comon.fsp.core.network.auth.RefreshRequest
import dev.comon.fsp.core.network.di.NetworkModule
import kotlinx.coroutines.runBlocking
import java.time.Clock
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * API-02 refresh with rotation (v1.4 §7.1). Single-flight: concurrent 401s share one refresh. The
 * Idempotency-Key survives transient failures and restarts so a lost response can be recovered; it is
 * dropped once a new pair is stored or the session ends. Runs on OkHttp threads, hence blocking.
 */
@Singleton
class SessionTokenRefresher @Inject constructor(
    private val credentials: SessionCredentials,
    @param:Named(NetworkModule.AUTH_CLIENT) private val authApi: ApiClient,
    private val deviceIds: DeviceIdProvider,
    private val sessions: AccountSessionStore,
    private val clock: Clock,
) : TokenRefresher {
    private val lock = Any()

    override fun refreshAfterUnauthorized(failedAccessToken: String?): String? = synchronized(lock) {
        credentials.accessToken()?.let { current -> if (current != failedAccessToken) return current }
        val userId = credentials.currentUserId ?: return null
        val refreshToken = credentials.refreshToken(userId) ?: return expire()
        val key = credentials.pendingRefreshKey()
        when (val result = authApi.callBlocking(AuthApi::class) { refresh(key, RefreshRequest(refreshToken, deviceIds.deviceId())) }) {
            is ApiResult.Success -> {
                credentials.store(userId, result.data.accessToken, result.data.refreshToken)
                result.data.accessToken
            }
            is ApiResult.Failure -> if (result.failure.endsSession()) expire() else null
        }
    }

    /** The server rejected the refresh token, account or device: sign in again is the only recovery. */
    private fun expire(): String? {
        credentials.clear()
        runBlocking { sessions.endOpen(clock.millis()) }
        return null
    }

    /** Transient failures (network, 408/429/5xx, OPERATION_IN_PROGRESS) keep the session and the key. */
    private fun ApiFailure.endsSession(): Boolean =
        this is ApiFailure.Http && !retryable && status in setOf(400, 401, 403, 409, 422)
}
