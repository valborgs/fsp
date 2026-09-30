package dev.comon.fsp.feature.dashboard

import dev.comon.fsp.domain.AccountSession
import dev.comon.fsp.domain.Role
import dev.comon.fsp.domain.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountHomeViewModelTest {
    private val account = AccountSession("u-1", "worker001", "홍길동", Role.INTERVIEWER)

    private class FakeSessions(initial: AccountSession?) : SessionRepository {
        val current = MutableStateFlow(initial)
        var logoutCalls = 0
        override fun observeCurrentAccount(): Flow<AccountSession?> = current
        override suspend fun restore(): AccountSession? = current.value
        override suspend fun logout() {
            logoutCalls++
            current.value = null
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun showsSignedInAccount() {
        val vm = AccountHomeViewModel(FakeSessions(account))
        assertEquals(AccountHomeUiState.SignedIn(account), vm.state.value)
    }

    @Test fun logoutEndsSessionAndReturnsToLogin() = runTest {
        val sessions = FakeSessions(account)
        val vm = AccountHomeViewModel(sessions)
        vm.onIntent(AccountHomeIntent.Logout)
        vm.onIntent(AccountHomeIntent.Logout)
        assertEquals(AccountHomeEffect.NavigateToLogin, vm.effects.first())
        assertEquals(1, sessions.logoutCalls)
    }

    @Test fun sessionEndedElsewhereReturnsToLogin() = runTest {
        val sessions = FakeSessions(account)
        val vm = AccountHomeViewModel(sessions)
        sessions.current.value = null // e.g. refresh rejected: TOKEN_REUSE_DETECTED or DEVICE_REVOKED
        assertEquals(AccountHomeEffect.NavigateToLogin, vm.effects.first())
    }

    @Test fun restoredEntryWithoutSessionLeavesImmediately() = runTest {
        val vm = AccountHomeViewModel(FakeSessions(null))
        assertEquals(AccountHomeEffect.NavigateToLogin, vm.effects.first())
        assertTrue(vm.state.value is AccountHomeUiState.Loading)
    }

    @Test fun roleLabels() {
        assertEquals(listOf("어드민", "슈퍼바이저", "조사원"), Role.entries.map { it.label() })
    }
}
