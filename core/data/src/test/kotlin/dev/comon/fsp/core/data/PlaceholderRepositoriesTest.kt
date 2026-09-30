package dev.comon.fsp.core.data

import dev.comon.fsp.domain.LoginFailure
import dev.comon.fsp.domain.LoginResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Until the server and Room exist, bindings must report real absence, never fake success or sample data. */
class PlaceholderRepositoriesTest {
    @Test fun loginWithoutServerFailsExplicitly() = runTest {
        assertEquals(
            LoginResult.Failure(LoginFailure.SERVER_NOT_CONFIGURED),
            UnconfiguredAuthRepository().login("worker001", "Secret!1234ab"),
        )
    }

    @Test fun noBundledSurveyDefinitions() = runTest {
        assertTrue(EmptySurveyCacheRepository().observeCachedSurveys().first().isEmpty())
    }
}
