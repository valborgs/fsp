package dev.comon.fsp.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Back stack keys and per-entry saved state survive saved-instance-state restoration (process death). */
@RunWith(AndroidJUnit4::class)
class FspNavigationRestorationTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun TestNavigation() {
        FspNavigation(
            startupContent = { signedOut, _ -> LaunchedEffect(Unit) { signedOut() } },
            loginContent = { open, signIn ->
                Column {
                    Button(onClick = open) { Text("open-offline") }
                    Button(onClick = signIn) { Text("sign-in") }
                }
            },
            accountHomeContent = { signOut -> Button(onClick = signOut) { Text("sign-out") } },
            offlineDashboardContent = { back ->
                var count by rememberSaveable { mutableIntStateOf(0) }
                Column {
                    Text("dashboard:$count")
                    Button(onClick = { count++ }) { Text("increment") }
                    Button(onClick = back) { Text("back-to-login") }
                }
            },
        )
    }

    @Test fun signedInStackIsRestoredAndSignOutReturnsToLogin() {
        val tester = StateRestorationTester(rule)
        tester.setContent { TestNavigation() }
        rule.onNodeWithText("sign-in").performClick()
        rule.onNodeWithText("sign-out").assertIsDisplayed()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("sign-out").performClick()
        rule.onNodeWithText("sign-in").assertIsDisplayed()
    }

    @Test fun backStackAndEntryStateAreRestored() {
        val tester = StateRestorationTester(rule)
        tester.setContent { TestNavigation() }
        rule.onNodeWithText("open-offline").performClick()
        rule.onNodeWithText("increment").performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("dashboard:1").assertIsDisplayed()
        rule.onNodeWithText("back-to-login").performClick()
        rule.onNodeWithText("open-offline").assertIsDisplayed()
    }
}
