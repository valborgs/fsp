package dev.comon.fsp.core.network

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Obtains a fresh access token after a 401. Implementations must be single-flight: when another
 * caller already replaced [failedAccessToken], return the current token without a new refresh.
 * Returns null when no valid session can be recovered (the 401 then reaches the caller).
 */
fun interface TokenRefresher {
    fun refreshAfterUnauthorized(failedAccessToken: String?): String?
}

/**
 * v1.4 §7.1/§11.3: on 401, refresh at most once per API request and resume the original request once.
 * The retried request bypasses application interceptors, so it gets its own `X-Request-ID` here.
 */
class TokenAuthenticator(private val refresher: TokenRefresher) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (AuthPaths.skipsRefresh(request.url)) return null
        if (response.priorResponse != null) return null // already refreshed once for this request
        val failedToken = request.header("Authorization")?.removePrefix("Bearer ")
        val token = refresher.refreshAfterUnauthorized(failedToken) ?: return null
        return request.newBuilder()
            .header("Authorization", "Bearer $token")
            .header(ApiClient.HEADER_REQUEST_ID, ClientHeadersInterceptor.newRequestId())
            .build()
    }
}
