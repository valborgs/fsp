package dev.comon.fsp.core.data

import dev.comon.fsp.domain.LoginFailure
import dev.comon.fsp.domain.LoginResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Until the server exists, login must report real absence, never fake success. */
class PlaceholderRepositoriesTest {
    @Test fun loginWithoutServerFailsExplicitly() = runTest {
        assertEquals(
            LoginResult.Failure(LoginFailure.SERVER_NOT_CONFIGURED),
            UnconfiguredAuthRepository().login("worker001", "Secret!1234ab"),
        )
    }
}
