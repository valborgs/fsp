package dev.comon.fsp.core.network

import kotlinx.serialization.Serializable

/**
 * Common response contract (API spec v1.4 §4.1, §5.1-5.5): `{data, meta}` on success, `{error, meta}`
 * on failure. The server always sends every field; defaults only keep parsing tolerant of proxies.
 */
@Serializable
data class ApiMeta(val requestId: String? = null, val serverTime: String? = null)

@Serializable
data class ApiSuccess<T>(val data: T, val meta: ApiMeta? = null)

@Serializable
data class ApiErrorBody(val error: ApiErrorDetail, val meta: ApiMeta? = null)

@Serializable
data class ApiErrorDetail(
    val code: String,
    val message: String? = null,
    val retryable: Boolean = false,
    val fields: List<ApiFieldError> = emptyList(),
    val details: ApiErrorDetails? = null,
)

@Serializable
data class ApiFieldError(val path: String, val code: String, val message: String? = null)

/** Values not relevant to an error are null (conflictingFields empty). */
@Serializable
data class ApiErrorDetails(
    val expectedSequence: Long? = null,
    val currentState: String? = null,
    val currentVersion: Long? = null,
    val retryAfterSeconds: Long? = null,
    val serverTime: String? = null,
    val conflictingFields: List<String> = emptyList(),
)
