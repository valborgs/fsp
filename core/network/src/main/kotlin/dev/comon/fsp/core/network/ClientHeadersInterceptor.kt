package dev.comon.fsp.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/** Stable per-installation identifier (UUID v4); a reinstall yields a new one. */
fun interface DeviceIdProvider {
    fun deviceId(): String
}

/** Current access token of the signed-in account, or null when there is no account session. */
fun interface AccessTokenProvider {
    fun accessToken(): String?
}

/** Paths with special authentication handling (v1.4 §4.1, §6). */
object AuthPaths {
    private const val LOGIN = "auth/login"
    private const val REFRESH = "auth/refresh"
    private const val LOGOUT = "auth/logout"

    /** login and refresh never carry `Authorization`. */
    fun isPublic(url: HttpUrl): Boolean = url.endsWith(LOGIN) || url.endsWith(REFRESH)

    /**
     * No automatic refresh-and-retry: login/refresh failures are final, and a retried logout would
     * send a refresh token that the refresh itself just rotated away.
     */
    fun skipsRefresh(url: HttpUrl): Boolean = isPublic(url) || url.endsWith(LOGOUT)

    private fun HttpUrl.endsWith(path: String) = encodedPath.trimEnd('/').endsWith("/$path")
}

/**
 * Adds the headers every request carries (`X-Request-ID` new per request, `X-Device-ID`,
 * `X-App-Version`) and the bearer token for every endpoint except login and refresh.
 */
class ClientHeadersInterceptor(
    private val deviceIdProvider: DeviceIdProvider,
    private val accessTokenProvider: AccessTokenProvider,
    private val appVersion: String,
    private val requestIdFactory: () -> String = ::newRequestId,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header(ApiClient.HEADER_REQUEST_ID, requestIdFactory())
            .header("X-Device-ID", deviceIdProvider.deviceId())
            .header("X-App-Version", appVersion)
        if (!AuthPaths.isPublic(original.url)) {
            accessTokenProvider.accessToken()?.let { builder.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(builder.build())
    }

    companion object {
        fun newRequestId(): String = UUID.randomUUID().toString()
    }
}
