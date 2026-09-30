package dev.comon.fsp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real Hilt graph: entry-scoped ViewModels are injected and effects drive navigation. */
@RunWith(AndroidJUnit4::class)
class MainActivityFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    /**
     * Startup restores the session first; without one it opens the login screen. A headless emulator
     * starts in non-touch mode, so the first field then takes focus and raises the keyboard.
     */
    @Before fun waitForLoginAndHideKeyboard() {
        rule.awaitDisplayed("오프라인 모드")
        rule.waitForIdle()
        Espresso.closeSoftKeyboard()
    }

    @Test fun loginWithoutServerShowsFailureInsteadOfSigningIn() {
        rule.onNodeWithText("아이디").performTextInput("worker001")
        rule.onNodeWithText("비밀번호").performTextInput("Secret!1234ab")
        rule.onNodeWithText("로그인").performScrollTo().performClick()
        // The keyboard may shrink the viewport; the message must be reachable by scrolling.
        rule.onNodeWithText("서버 연결이 아직 설정되지 않았습니다", substring = true)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("오프라인 모드").performScrollTo().assertIsDisplayed()
    }

    @Test fun offlineModeWithoutCacheShowsNoSurveyAndSurvivesRecreation() {
        rule.onNodeWithText("오프라인 모드").performScrollTo().performClick()
        rule.awaitDisplayed("설문 데이터가 없습니다")

        rule.activityRule.scenario.recreate()
        rule.awaitDisplayed("설문 데이터가 없습니다")

        rule.onNodeWithText("로그인 화면으로 돌아가기").performScrollTo().performClick()
        rule.awaitDisplayed("로그인")
    }

    @Test fun systemBackFromOfflineModeReturnsToLogin() {
        rule.onNodeWithText("오프라인 모드").performScrollTo().performClick()
        rule.awaitDisplayed("설문 데이터가 없습니다")
        Espresso.closeSoftKeyboard() // Back would otherwise only dismiss the keyboard.
        Espresso.pressBack()
        rule.awaitDisplayed("로그인")
    }

    /**
     * Navigation transitions animate; wait (bounded) until the destination is actually on screen.
     * Clicks scroll first: on a non-touch-mode emulator the first field takes focus, the keyboard
     * shrinks the viewport, and buttons below it are only reachable by scrolling, as for a user.
     */
    private fun ComposeTestRule.awaitDisplayed(text: String) {
        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithText(text).performScrollTo().assertIsDisplayed() }.isSuccess }
    }
}
