package dev.comon.fsp.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.comon.fsp.core.data.RoomSurveyCacheRepository
import dev.comon.fsp.core.data.UnconfiguredAuthRepository
import dev.comon.fsp.domain.AuthRepository
import dev.comon.fsp.domain.SurveyCacheRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindAuthRepository(impl: UnconfiguredAuthRepository): AuthRepository

    @Binds
    abstract fun bindSurveyCacheRepository(impl: RoomSurveyCacheRepository): SurveyCacheRepository
}
