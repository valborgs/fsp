package dev.comon.fsp.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.comon.fsp.core.network.ApiClient
import dev.comon.fsp.core.network.DeviceIdProvider

/**
 * Requests the network graph from the application component so a missing binding fails compilation
 * even before any screen uses the network (Dagger does not report missing bindings of unused modules).
 * Also used by instrumented tests. Remove once stage 2 injects [ApiClient] into a repository.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NetworkGraphEntryPoint {
    fun apiClient(): ApiClient
    fun deviceIdProvider(): DeviceIdProvider
}
