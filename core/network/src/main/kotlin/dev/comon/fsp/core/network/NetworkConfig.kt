package dev.comon.fsp.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Server location and client identity. [baseUrl] is null when the build has no API address; callers
 * then get [ApiFailure.NotConfigured] without any network call.
 */
class NetworkConfig private constructor(val baseUrl: HttpUrl?, val appVersion: String) {
    companion object {
        /** v1.4 §4.1: X-App-Version is 1-32 chars of `[A-Za-z0-9._+-]`. */
        private val APP_VERSION = Regex("^[A-Za-z0-9._+-]{1,32}$")

        /**
         * Parses the configured base URL (e.g. `https://host/api/v1`). Blank means not configured.
         * Production accepts HTTPS only; [allowCleartext] exists for local contract tests.
         */
        fun parse(rawBaseUrl: String, appVersion: String, allowCleartext: Boolean = false): NetworkConfig {
            require(APP_VERSION.matches(appVersion)) { "App version is not a valid X-App-Version" }
            val trimmed = rawBaseUrl.trim()
            if (trimmed.isEmpty()) return NetworkConfig(null, appVersion)
            val url = requireNotNull(trimmed.trimEnd('/').plus("/").toHttpUrlOrNull()) { "Invalid API base URL" }
            require(url.isHttps || allowCleartext) { "API base URL must use HTTPS" }
            return NetworkConfig(url, appVersion)
        }
    }
}
