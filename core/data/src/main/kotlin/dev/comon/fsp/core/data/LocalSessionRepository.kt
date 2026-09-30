package dev.comon.fsp.core.data

import dev.comon.fsp.core.network.ApiClient
import dev.comon.fsp.core.network.TokenRefresher
import dev.comon.fsp.core.network.auth.AuthApi
import dev.comon.fsp.core.network.auth.LogoutRequest
import dev.comon.fsp.domain.AccountSession
import dev.comon.fsp.domain.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject

class LocalSessionRepository @Inject constructor(
    private val sessions: AccountSessionStore,
    private val credentials: SessionCredentials,
    private val api: ApiClient,
    private val refresher: TokenRefresher,
    private val clock: Clock,
) : SessionRepository {
    override fun observeCurrentAccount(): Flow<AccountSession?> = sessions.observeCurrent().map { it?.toAccountSession() }

    override suspend fun restore(): AccountSession? {
        val account = sessions.current()?.toAccountSession() ?: return null
        val restored = withContext(Dispatchers.IO) { credentials.activate(account.userId) }
        if (!restored) {
            sessions.endOpen(clock.millis())
            return null
        }
        return account
    }

    override suspend fun logout() {
        credentials.currentUserId?.let { revokeOnServer(it) }
        credentials.clear()
        sessions.endOpen(clock.millis())
    }

    /**
     * API-03 needs a bearer token and the current refresh token. Best effort: its outcome never blocks
     * the local sign-out (401 for an already revoked session also means signed out).
     */
    private suspend fun revokeOnServer(userId: String) = withContext(Dispatchers.IO) {
        if (credentials.accessToken() == null) refresher.refreshAfterUnauthorized(null)
        val refreshToken = credentials.refreshToken(userId) ?: return@withContext
        api.callNoContent(AuthApi::class) { logout(LogoutRequest(refreshToken)) }
    }
}
