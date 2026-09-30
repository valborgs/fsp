package dev.comon.fsp.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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

    @Test fun backStackAndEntryStateAreRestored() {
        val tester = StateRestorationTester(rule)
        tester.setContent {
            FspNavigation(
                loginContent = { open -> Button(onClick = open) { Text("open-offline") } },
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
        rule.onNodeWithText("open-offline").performClick()
        rule.onNodeWithText("increment").performClick()

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithText("dashboard:1").assertIsDisplayed()
        rule.onNodeWithText("back-to-login").performClick()
        rule.onNodeWithText("open-offline").assertIsDisplayed()
    }
}
