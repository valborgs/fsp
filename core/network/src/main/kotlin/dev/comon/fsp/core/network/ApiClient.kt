package dev.comon.fsp.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Entry point for API calls. Converts every outcome into [ApiResult]; only coroutine cancellation
 * propagates as an exception. Without a configured base URL no Retrofit instance exists.
 */
class ApiClient @Inject constructor(
    config: NetworkConfig,
    okHttpClient: OkHttpClient,
    private val json: Json,
    private val clock: Clock,
) {
    private val retrofit: Retrofit? = config.baseUrl?.let {
        Retrofit.Builder()
            .baseUrl(it)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
    private val services = ConcurrentHashMap<KClass<*>, Any>()

    suspend fun <S : Any, T> call(
        service: KClass<S>,
        request: suspend S.() -> Response<ApiSuccess<T>>,
    ): ApiResult<T> {
        val api = service(service) ?: return ApiResult.Failure(ApiFailure.NotConfigured)
        return guard { envelope(api.request()) }
    }

    /** For endpoints answering 204 No Content (e.g. logout). */
    suspend fun <S : Any> callNoContent(service: KClass<S>, request: suspend S.() -> Response<Unit>): ApiResult<Unit> {
        val api = service(service) ?: return ApiResult.Failure(ApiFailure.NotConfigured)
        return guard {
            val response = api.request()
            if (response.isSuccessful) ApiResult.Success(Unit, null) else ApiResult.Failure(httpFailure(response))
        }
    }

    /** Blocking variant for OkHttp callback threads (token refresh inside an Authenticator). */
    fun <S : Any, T> callBlocking(service: KClass<S>, request: S.() -> Call<ApiSuccess<T>>): ApiResult<T> {
        val api = service(service) ?: return ApiResult.Failure(ApiFailure.NotConfigured)
        return guard { envelope(api.request().execute()) }
    }

    private fun <S : Any> service(type: KClass<S>): S? {
        val client = retrofit ?: return null
        @Suppress("UNCHECKED_CAST")
        return services.getOrPut(type) { client.create(type.java) } as S
    }

    private fun <T> envelope(response: Response<ApiSuccess<T>>): ApiResult<T> = if (response.isSuccessful) {
        response.body()?.let { ApiResult.Success(it.data, it.meta) } ?: ApiResult.Failure(ApiFailure.MalformedResponse)
    } else {
        ApiResult.Failure(httpFailure(response))
    }

    private inline fun <T> guard(block: () -> ApiResult<T>): ApiResult<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: InterruptedIOException) {
        // SocketTimeoutException and OkHttp call timeouts.
        ApiResult.Failure(ApiFailure.Network(NetworkFailureReason.TIMEOUT))
    } catch (_: IOException) {
        ApiResult.Failure(ApiFailure.Network(NetworkFailureReason.CONNECTIVITY))
    } catch (_: SerializationException) {
        ApiResult.Failure(ApiFailure.MalformedResponse)
    }

    private fun httpFailure(response: Response<*>): ApiFailure.Http {
        val body = runCatching { response.errorBody()?.string() }.getOrNull()
        val error = body?.let { runCatching { json.decodeFromString<ApiErrorBody>(it) }.getOrNull() }
        val retryAfterSeconds = error?.error?.details?.retryAfterSeconds
        return ApiFailure.Http(
            status = response.code(),
            kind = HttpFailureKind.of(response.code()),
            code = error?.error?.code,
            fields = error?.error?.fields.orEmpty(),
            details = error?.error?.details,
            requestId = error?.meta?.requestId ?: response.headers()[HEADER_REQUEST_ID],
            retryAfterMillis = parseRetryAfter(response.headers()["Retry-After"], clock.instant())
                ?: retryAfterSeconds?.takeIf { it >= 0 }?.times(1_000),
        )
    }

    companion object {
        const val HEADER_REQUEST_ID = "X-Request-ID"

        /** Retry-After as delta-seconds or HTTP-date; null when absent or unparsable. Never negative. */
        fun parseRetryAfter(value: String?, now: Instant): Long? {
            val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            raw.toLongOrNull()?.let { return if (it >= 0) it * 1_000 else null }
            val date = runCatching { ZonedDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull()
                ?: return null
            return Duration.between(now, date.toInstant()).toMillis().coerceAtLeast(0)
        }
    }
}
