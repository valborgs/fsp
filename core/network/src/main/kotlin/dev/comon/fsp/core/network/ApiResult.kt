package dev.comon.fsp.core.network

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T, val meta: ApiMeta?) : ApiResult<T>
    data class Failure(val failure: ApiFailure) : ApiResult<Nothing>
}

/**
 * Why a call did not succeed. [retryable] marks transient failures only (v1.4 §11.3: connection
 * failure, timeout, 408, 429, 5xx and 409 OPERATION_IN_PROGRESS); the retry budget itself
 * (initial call + 3 retries) is applied by the sync layer. 401 is handled by token refresh, not retry.
 */
sealed interface ApiFailure {
    val retryable: Boolean

    /** No API base URL is configured for this build; no request was sent. */
    data object NotConfigured : ApiFailure {
        override val retryable = false
    }

    data class Network(val reason: NetworkFailureReason) : ApiFailure {
        override val retryable = true
    }

    data class Http(
        val status: Int,
        val kind: HttpFailureKind,
        /** Server error code (§13), or null when the body is not the error envelope (e.g. proxy HTML). */
        val code: String?,
        val fields: List<ApiFieldError> = emptyList(),
        val details: ApiErrorDetails? = null,
        val requestId: String? = null,
        val retryAfterMillis: Long? = null,
    ) : ApiFailure {
        override val retryable: Boolean get() = kind.transient || code == CODE_OPERATION_IN_PROGRESS
    }

    /** A 2xx response whose body does not match the contract. Not retried automatically. */
    data object MalformedResponse : ApiFailure {
        override val retryable = false
    }

    companion object {
        const val CODE_OPERATION_IN_PROGRESS = "OPERATION_IN_PROGRESS"
    }
}

enum class NetworkFailureReason { TIMEOUT, CONNECTIVITY }

enum class HttpFailureKind(val transient: Boolean) {
    BAD_REQUEST(false),
    UNAUTHORIZED(false),
    FORBIDDEN(false),
    NOT_FOUND(false),
    METHOD_NOT_ALLOWED(false),
    REQUEST_TIMEOUT(true),
    CONFLICT(false),
    RETENTION_EXPIRED(false),
    PRECONDITION_FAILED(false),
    PAYLOAD_TOO_LARGE(false),
    UNSUPPORTED_MEDIA_TYPE(false),
    UNPROCESSABLE(false),
    PRECONDITION_REQUIRED(false),
    TOO_MANY_REQUESTS(true),
    SERVER_ERROR(true),
    UNEXPECTED(false),
    ;

    companion object {
        fun of(status: Int): HttpFailureKind = when (status) {
            400 -> BAD_REQUEST
            401 -> UNAUTHORIZED
            403 -> FORBIDDEN
            404 -> NOT_FOUND
            405 -> METHOD_NOT_ALLOWED
            408 -> REQUEST_TIMEOUT
            409 -> CONFLICT
            410 -> RETENTION_EXPIRED
            412 -> PRECONDITION_FAILED
            413 -> PAYLOAD_TOO_LARGE
            415 -> UNSUPPORTED_MEDIA_TYPE
            422 -> UNPROCESSABLE
            428 -> PRECONDITION_REQUIRED
            429 -> TOO_MANY_REQUESTS
            in 500..599 -> SERVER_ERROR
            else -> UNEXPECTED
        }
    }
}
