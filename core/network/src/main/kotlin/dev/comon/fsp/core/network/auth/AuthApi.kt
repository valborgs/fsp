package dev.comon.fsp.core.network.auth

import dev.comon.fsp.core.network.ApiSuccess
import dev.comon.fsp.core.network.ClientHeadersInterceptor
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface AuthApi {
    /** Public endpoint: never sends a stale bearer token. */
    @Headers(ClientHeadersInterceptor.NO_AUTH)
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiSuccess<LoginResponse>>
}

/** Field names follow the API contract (`id`, `pw`). The password is sent as typed. */
@Serializable
data class LoginRequest(val id: String, val pw: String, val deviceId: String) {
    override fun toString(): String = "LoginRequest(id=$id, pw=***, deviceId=$deviceId)"
}

@Serializable
data class LoginResponse(
    val accessToken: String,
    val expiresIn: Long,
    val refreshToken: String,
    val user: UserDto,
    val credentialVersion: Int,
) {
    override fun toString(): String =
        "LoginResponse(accessToken=***, expiresIn=$expiresIn, refreshToken=***, user=$user, " +
            "credentialVersion=$credentialVersion)"
}

/** `resourceVersion` is omitted until its wire type is fixed; unknown keys are ignored. */
@Serializable
data class UserDto(
    val userId: String,
    val id: String,
    val name: String,
    val grade: Int,
    val role: String,
    val active: Boolean,
)
