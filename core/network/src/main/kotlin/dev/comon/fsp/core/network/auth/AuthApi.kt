package dev.comon.fsp.core.network.auth

import dev.comon.fsp.core.network.ApiSuccess
import kotlinx.serialization.Serializable
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/** API-01..04 of spec v1.4. login/refresh are public (no bearer); logout and me need one. */
interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiSuccess<LoginResultDto>>

    /** Blocking so it can run inside an OkHttp Authenticator. Idempotency-Key is mandatory. */
    @POST("auth/refresh")
    fun refresh(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: RefreshRequest,
    ): Call<ApiSuccess<TokenPairDto>>

    /** 204 No Content. */
    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<Unit>

    @GET("me")
    suspend fun me(): Response<ApiSuccess<MeResultDto>>
}

/** Field names follow the API contract (`id`, `pw`). The password is sent as typed. */
@Serializable
data class LoginRequest(val id: String, val pw: String, val deviceId: String) {
    override fun toString(): String = "LoginRequest(id=$id, pw=***, deviceId=$deviceId)"
}

@Serializable
data class RefreshRequest(val refreshToken: String, val deviceId: String) {
    override fun toString(): String = "RefreshRequest(refreshToken=***, deviceId=$deviceId)"
}

@Serializable
data class LogoutRequest(val refreshToken: String) {
    override fun toString(): String = "LogoutRequest(refreshToken=***)"
}

/** §5.7 TokenPair. Tokens are opaque 43-char base64url strings; never logged. */
@Serializable
data class TokenPairDto(
    val tokenType: String,
    val accessToken: String,
    val expiresIn: Long,
    val accessExpiresAt: String,
    val refreshToken: String,
    val refreshExpiresIn: Long,
    val refreshExpiresAt: String,
    val sessionId: String,
    val credentialVersion: Long,
) {
    override fun toString(): String =
        "TokenPairDto(tokenType=$tokenType, accessToken=***, expiresIn=$expiresIn, accessExpiresAt=$accessExpiresAt, " +
            "refreshToken=***, refreshExpiresAt=$refreshExpiresAt, sessionId=$sessionId, credentialVersion=$credentialVersion)"
}

/** §5.8. For interviewers activeDeviceId is this device and deviceNextSequence the next attendance sequence. */
@Serializable
data class LoginResultDto(
    val tokens: TokenPairDto,
    val user: UserDto,
    val activeDeviceId: String?,
    val deviceNextSequence: Long?,
)

/** §5.6 User. */
@Serializable
data class UserDto(
    val userId: String,
    val id: String,
    val name: String,
    val grade: Int,
    val role: String,
    val active: Boolean,
    val resourceVersion: Long,
)

/** §5.10 MeResult. The anonymous import policy is read when that feature lands (stage 5). */
@Serializable
data class MeResultDto(
    val user: UserDto,
    val credentialVersion: Long,
    val activeDeviceId: String?,
    val deviceNextSequence: Long?,
)
