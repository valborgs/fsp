package dev.comon.fsp.core.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.comon.fsp.core.data.InstallationIdStore
import dev.comon.fsp.core.network.AccessTokenProvider
import dev.comon.fsp.core.network.DeviceIdProvider
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkIdentityModule {
    @Provides
    @Singleton
    fun provideDeviceIdProvider(@ApplicationContext context: Context): DeviceIdProvider =
        InstallationIdStore(File(context.noBackupFilesDir, "installation-id"))

    /** No account session exists until login and encrypted token storage land (stage 2 / 1B-4). */
    @Provides
    fun provideAccessTokenProvider(): AccessTokenProvider = AccessTokenProvider { null }
}
