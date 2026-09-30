package dev.comon.fsp.core.security.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.comon.fsp.core.security.DataCipher
import dev.comon.fsp.core.security.KeystoreDataCipher
import dev.comon.fsp.core.security.RefreshTokenStore
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideDataCipher(): DataCipher = KeystoreDataCipher(KeystoreDataCipher.DATA_KEY_ALIAS)

    @Provides
    @Singleton
    fun provideRefreshTokenStore(@ApplicationContext context: Context, cipher: DataCipher): RefreshTokenStore =
        RefreshTokenStore(File(context.noBackupFilesDir, "session/refresh-token"), cipher)
}
