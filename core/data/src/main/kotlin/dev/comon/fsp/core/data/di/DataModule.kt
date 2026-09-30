package dev.comon.fsp.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.comon.fsp.core.data.AccountSessionStore
import dev.comon.fsp.core.data.LocalSessionRepository
import dev.comon.fsp.core.data.NetworkAuthRepository
import dev.comon.fsp.core.data.RoomAccountSessionStore
import dev.comon.fsp.core.data.RoomSurveyCacheRepository
import dev.comon.fsp.core.data.SessionTokenRefresher
import dev.comon.fsp.core.network.TokenRefresher
import dev.comon.fsp.domain.AuthRepository
import dev.comon.fsp.domain.SessionRepository
import dev.comon.fsp.domain.SurveyCacheRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAuthRepository(impl: NetworkAuthRepository): AuthRepository

    @Binds
    abstract fun bindSessionRepository(impl: LocalSessionRepository): SessionRepository

    @Binds
    abstract fun bindTokenRefresher(impl: SessionTokenRefresher): TokenRefresher

    @Binds
    abstract fun bindAccountSessionStore(impl: RoomAccountSessionStore): AccountSessionStore

    @Binds
    abstract fun bindSurveyCacheRepository(impl: RoomSurveyCacheRepository): SurveyCacheRepository
}
