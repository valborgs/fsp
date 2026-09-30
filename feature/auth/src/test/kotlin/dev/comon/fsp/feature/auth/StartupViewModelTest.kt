package dev.comon.fsp.feature.auth

import dev.comon.fsp.domain.AccountSession
import dev.comon.fsp.domain.Role
import dev.comon.fsp.domain.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupViewModelTest {
    private class FakeSessions(private val restore: () -> AccountSession?) : SessionRepository {
        override fun observeCurrentAccount(): Flow<AccountSession?> = emptyFlow()
        override suspend fun restore(): AccountSession? = restore.invoke()
        override suspend fun logout() = Unit
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun storedSessionOpensAccountHome() = runTest {
        val vm = StartupViewModel(FakeSessions { AccountSession("u-1", "worker001", "홍길동", Role.INTERVIEWER) })
        assertEquals(StartupEffect.NavigateToAccountHome, vm.effects.first())
    }

    @Test fun noSessionOpensLogin() = runTest {
        assertEquals(StartupEffect.NavigateToLogin, StartupViewModel(FakeSessions { null }).effects.first())
    }

    @Test fun unreadableLocalStateFallsBackToLogin() = runTest {
        assertEquals(StartupEffect.NavigateToLogin, StartupViewModel(FakeSessions { error("db") }).effects.first())
    }
}
