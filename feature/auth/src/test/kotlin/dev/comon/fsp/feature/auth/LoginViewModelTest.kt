package dev.comon.fsp.feature.auth

import androidx.lifecycle.SavedStateHandle
import dev.comon.fsp.domain.AuthRepository
import dev.comon.fsp.domain.LoginFailure
import dev.comon.fsp.domain.LoginResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private class FakeAuthRepository(private val gate: CompletableDeferred<Unit>? = null) : AuthRepository {
        val calls = mutableListOf<Pair<String, String>>()
        override suspend fun login(loginId: String, password: String): LoginResult {
            calls += loginId to password
            gate?.await()
            return LoginResult.Failure(LoginFailure.SERVER_NOT_CONFIGURED)
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun LoginViewModel.type(id: String, pw: String) {
        onIntent(LoginIntent.LoginIdChanged(id))
        onIntent(LoginIntent.PasswordChanged(pw))
    }

    @Test fun passwordNeverReachesSavedStateAndIsGoneAfterProcessRecreation() {
        val handle = SavedStateHandle()
        LoginViewModel(FakeAuthRepository(), handle).type("worker001", "Secret!1234ab")
        assertEquals(setOf("loginId"), handle.keys())
        assertFalse(handle.keys().any { handle.get<Any>(it) == "Secret!1234ab" })

        // A new ViewModel on the same handle models restoration after process death.
        val restored = LoginViewModel(FakeAuthRepository(), handle).state.value
        assertEquals("worker001", restored.loginId)
        assertEquals("", restored.password)
    }

    @Test fun unconfiguredServerIsReportedAsFailureAndPasswordIsCleared() {
        val repo = FakeAuthRepository()
        val vm = LoginViewModel(repo, SavedStateHandle())
        vm.type(" worker001 ", " pass word! ")
        vm.onIntent(LoginIntent.Submit)

        // Login ID is trimmed; the password is passed exactly as typed.
        assertEquals(listOf("worker001" to " pass word! "), repo.calls)
        val state = vm.state.value
        assertEquals(LoginFailure.SERVER_NOT_CONFIGURED, state.failure)
        assertEquals("", state.password)
        assertFalse(state.submitting)
    }

    @Test fun blankInputDoesNotCallRepository() {
        val repo = FakeAuthRepository()
        val vm = LoginViewModel(repo, SavedStateHandle())
        vm.onIntent(LoginIntent.Submit)
        vm.type("   ", "pw")
        vm.onIntent(LoginIntent.Submit)
        vm.type("worker001", "")
        vm.onIntent(LoginIntent.Submit)
        assertTrue(repo.calls.isEmpty())
    }

    @Test fun repeatedSubmitWhileInFlightCallsRepositoryOnce() {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeAuthRepository(gate)
        val vm = LoginViewModel(repo, SavedStateHandle())
        vm.type("worker001", "Secret!1234ab")
        vm.onIntent(LoginIntent.Submit)
        assertTrue(vm.state.value.submitting)
        vm.onIntent(LoginIntent.Submit)
        gate.complete(Unit)
        assertEquals(1, repo.calls.size)
        assertFalse(vm.state.value.submitting)
    }

    @Test fun editingClearsPreviousFailure() {
        val vm = LoginViewModel(FakeAuthRepository(), SavedStateHandle())
        vm.type("worker001", "pw")
        vm.onIntent(LoginIntent.Submit)
        vm.onIntent(LoginIntent.PasswordChanged("pw2"))
        assertNull(vm.state.value.failure)
    }

    @Test fun offlineModeMakesNoAuthCallAndClearsPassword() = runTest {
        val repo = FakeAuthRepository()
        val vm = LoginViewModel(repo, SavedStateHandle())
        vm.type("worker001", "Secret!1234ab")
        vm.onIntent(LoginIntent.EnterOfflineMode)
        assertEquals(LoginEffect.OpenOfflineMode, vm.effects.first())
        assertTrue(repo.calls.isEmpty())
        assertEquals("", vm.state.value.password)
    }

    @Test fun debugStringsMaskPassword() {
        assertFalse(LoginUiState(password = "Secret!1234ab").toString().contains("Secret"))
        assertFalse(LoginIntent.PasswordChanged("Secret!1234ab").toString().contains("Secret"))
    }
}
