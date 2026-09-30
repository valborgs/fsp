package dev.comon.fsp.core.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.comon.fsp.core.data.AccessTokenHolder
import dev.comon.fsp.core.data.InstallationIdStore
import dev.comon.fsp.core.data.RefreshKeyStore
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

    /** In-memory token of the signed-in account; null until sign-in or the first refresh after restore. */
    @Provides
    fun provideAccessTokenProvider(holder: AccessTokenHolder): AccessTokenProvider = holder

    @Provides
    @Singleton
    fun provideRefreshKeyStore(@ApplicationContext context: Context): RefreshKeyStore =
        RefreshKeyStore(File(context.noBackupFilesDir, "session/refresh-idempotency-key"))
}
