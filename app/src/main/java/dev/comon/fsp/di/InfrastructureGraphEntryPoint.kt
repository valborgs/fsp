package dev.comon.fsp.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.comon.fsp.core.data.OutboxOperations
import dev.comon.fsp.core.data.SessionCredentials
import dev.comon.fsp.core.network.AccessTokenProvider
import dev.comon.fsp.core.network.ApiClient
import dev.comon.fsp.core.network.DeviceIdProvider

/**
 * Requests infrastructure no screen uses yet (network, credentials, encrypted Outbox) from the
 * application component, so a missing binding fails compilation (Dagger does not report missing
 * bindings of unused modules). Also used by instrumented tests. Shrink it as stages 2-3 inject
 * these types into repositories.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface InfrastructureGraphEntryPoint {
    fun apiClient(): ApiClient
    fun deviceIdProvider(): DeviceIdProvider
    fun accessTokenProvider(): AccessTokenProvider
    fun sessionCredentials(): SessionCredentials
    fun outboxOperations(): OutboxOperations
}
