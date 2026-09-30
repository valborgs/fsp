package dev.comon.fsp.core.data

import dev.comon.fsp.core.network.AccessTokenProvider
import dev.comon.fsp.core.security.RefreshTokenStore
import javax.inject.Inject
import javax.inject.Singleton

/** Access token of the current account. Memory only: it is never written to disk or saved state. */
@Singleton
class AccessTokenHolder @Inject constructor() : AccessTokenProvider {
    @Volatile private var token: String? = null

    override fun accessToken(): String? = token

    fun set(accessToken: String) {
        token = accessToken
    }

    fun clear() {
        token = null
    }
}

/**
 * Single place that stores and clears account credentials. Sign-in and every refresh call [store];
 * logout and expiry call [clear], which drops the tokens but leaves the account's unsent records.
 */
@Singleton
class SessionCredentials @Inject constructor(
    private val accessTokens: AccessTokenHolder,
    private val refreshTokens: RefreshTokenStore,
    private val refreshKeys: RefreshKeyStore,
) {
    /** Account whose tokens are in use; set on sign-in and on restore at app start. */
    @Volatile var currentUserId: String? = null
        private set

    /** Saves a new token pair. A rotated refresh token also ends the pending refresh attempt. */
    fun store(userId: String, accessToken: String, refreshToken: String) {
        refreshTokens.save(userId, refreshToken)
        refreshKeys.clear()
        accessTokens.set(accessToken)
        currentUserId = userId
    }

    /** Re-attaches a stored session after process start. False when its refresh token is gone. */
    fun activate(userId: String): Boolean {
        if (refreshTokens.read(userId) == null) return false
        currentUserId = userId
        return true
    }

    fun accessToken(): String? = accessTokens.accessToken()

    fun refreshToken(userId: String): String? = refreshTokens.read(userId)

    /**
     * Idempotency-Key of the refresh in progress. Persisted so a retry after a lost response or a
     * process restart reuses it and the server can return the same rotation result (v1.4 §7.1).
     */
    fun pendingRefreshKey(): String = refreshKeys.getOrCreate()

    fun clear() {
        accessTokens.clear()
        refreshTokens.clear()
        refreshKeys.clear()
        currentUserId = null
    }
}
