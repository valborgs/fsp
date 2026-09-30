package dev.comon.fsp.core.network

import kotlinx.serialization.Serializable

/** Common response contract (spec section 11): `{data, meta}` on success, `{error, meta}` on failure. */
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
)

@Serializable
data class ApiFieldError(val path: String, val code: String)
