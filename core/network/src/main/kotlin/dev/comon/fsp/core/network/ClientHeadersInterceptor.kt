package dev.comon.fsp.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/** Stable per-installation identifier; a reinstall yields a new one. */
fun interface DeviceIdProvider {
    fun deviceId(): String
}

/** Current access token of the signed-in account, or null when there is no account session. */
fun interface AccessTokenProvider {
    fun accessToken(): String?
}

/**
 * Adds the tracing headers every request carries and the bearer token for authenticated endpoints.
 * Public endpoints opt out with [NO_AUTH]; the marker never leaves the device.
 */
class ClientHeadersInterceptor(
    private val deviceIdProvider: DeviceIdProvider,
    private val accessTokenProvider: AccessTokenProvider,
    private val appVersion: String,
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val public = original.header(NO_AUTH_HEADER) != null
        val builder = original.newBuilder()
            .removeHeader(NO_AUTH_HEADER)
            .header(ApiClient.HEADER_REQUEST_ID, requestIdFactory())
            .header("X-Device-ID", deviceIdProvider.deviceId())
            .header("X-App-Version", appVersion)
        if (!public) {
            accessTokenProvider.accessToken()?.let { builder.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(builder.build())
    }

    companion object {
        private const val NO_AUTH_HEADER = "X-Fsp-No-Auth"

        /** Use as `@Headers(ClientHeadersInterceptor.NO_AUTH)` on endpoints that must not send a token. */
        const val NO_AUTH = "$NO_AUTH_HEADER: true"
    }
}
