package dev.comon.fsp.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.comon.fsp.BuildConfig
import dev.comon.fsp.core.network.NetworkConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    /** Base URL comes from the `fsp.apiBaseUrl` Gradle property; empty means not configured. */
    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig = NetworkConfig.parse(BuildConfig.API_BASE_URL, BuildConfig.VERSION_NAME)
}
