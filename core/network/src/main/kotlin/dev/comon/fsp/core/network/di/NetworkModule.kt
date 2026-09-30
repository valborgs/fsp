package dev.comon.fsp.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.comon.fsp.core.network.AccessTokenProvider
import dev.comon.fsp.core.network.ApiClient
import dev.comon.fsp.core.network.ClientHeadersInterceptor
import dev.comon.fsp.core.network.DeviceIdProvider
import dev.comon.fsp.core.network.NetworkConfig
import dev.comon.fsp.core.network.TokenAuthenticator
import dev.comon.fsp.core.network.TokenRefresher
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /** Client without the refresh Authenticator; used only to call `/auth/refresh` itself. */
    const val AUTH_CLIENT = "authClient"

    @Provides
    @Singleton
    fun provideJson(): Json = apiJson()

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()

    /** No HTTP body/header logging: login bodies carry passwords and responses carry tokens. */
    @Provides
    @Singleton
    @Named(AUTH_CLIENT)
    fun provideAuthOkHttpClient(
        config: NetworkConfig,
        deviceIdProvider: DeviceIdProvider,
        accessTokenProvider: AccessTokenProvider,
    ): OkHttpClient = apiOkHttpClient(ClientHeadersInterceptor(deviceIdProvider, accessTokenProvider, config.appVersion))

    @Provides
    @Singleton
    fun provideOkHttpClient(@Named(AUTH_CLIENT) base: OkHttpClient, refresher: TokenRefresher): OkHttpClient =
        base.newBuilder().authenticator(TokenAuthenticator(refresher)).build()

    @Provides
    @Singleton
    @Named(AUTH_CLIENT)
    fun provideAuthApiClient(
        config: NetworkConfig,
        @Named(AUTH_CLIENT) client: OkHttpClient,
        json: Json,
        clock: Clock,
    ): ApiClient = ApiClient(config, client, json, clock)

    fun apiJson(): Json = Json { ignoreUnknownKeys = true }

    fun apiOkHttpClient(headers: ClientHeadersInterceptor, readTimeoutMillis: Long = 30_000): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(headers)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            // HTTP-level retries belong to the Outbox policy (initial call + 3 retries), not OkHttp.
            .retryOnConnectionFailure(false)
            .build()
}
