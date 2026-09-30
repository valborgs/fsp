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
 * Single place that stores and clears account credentials. Sign-in (stage 2) calls [store];
 * logout calls [clear], which drops both tokens but leaves the account's unsent records in place.
 */
@Singleton
class SessionCredentials @Inject constructor(
    private val accessTokens: AccessTokenHolder,
    private val refreshTokens: RefreshTokenStore,
) {
    fun store(userId: String, accessToken: String, refreshToken: String) {
        refreshTokens.save(userId, refreshToken)
        accessTokens.set(accessToken)
    }

    fun refreshToken(userId: String): String? = refreshTokens.read(userId)

    fun clear() {
        accessTokens.clear()
        refreshTokens.clear()
    }
}
