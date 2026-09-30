package dev.comon.fsp.navigation

import androidx.navigation3.runtime.NavKey
import dev.comon.fsp.core.navigation.AccountHomeRoute
import dev.comon.fsp.core.navigation.LoginRoute
import dev.comon.fsp.core.navigation.OfflineDashboardRoute
import dev.comon.fsp.core.navigation.StartupRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class BackStackOpsTest {
    private data object Other : NavKey

    @Test fun navigateToIgnoresDuplicateTop() {
        val stack = mutableListOf<NavKey>(LoginRoute)
        stack.navigateTo(OfflineDashboardRoute)
        stack.navigateTo(OfflineDashboardRoute)
        assertEquals(listOf(LoginRoute, OfflineDashboardRoute), stack)
    }

    @Test fun popToRemovesEverythingAboveRoot() {
        val stack = mutableListOf(LoginRoute, OfflineDashboardRoute, Other)
        stack.popTo(LoginRoute)
        assertEquals(listOf<NavKey>(LoginRoute), stack)
    }

    @Test fun popToMissingRootReplacesStackWithoutEmptyingIt() {
        val stack = mutableListOf<NavKey>(OfflineDashboardRoute, Other)
        stack.popTo(LoginRoute)
        assertEquals(listOf<NavKey>(LoginRoute), stack)
    }

    @Test fun signInReplacesTheWholeStack() {
        val stack = mutableListOf<NavKey>(StartupRoute, LoginRoute)
        stack.resetTo(AccountHomeRoute)
        assertEquals(listOf<NavKey>(AccountHomeRoute), stack)
    }

    @Test fun resetToCurrentSingleEntryIsNoOp() {
        val stack = mutableListOf<NavKey>(LoginRoute)
        stack.resetTo(LoginRoute)
        assertEquals(listOf<NavKey>(LoginRoute), stack)
    }

    @Test fun signOutFromAccountLeavesOnlyLogin() {
        val stack = mutableListOf<NavKey>(AccountHomeRoute, Other)
        stack.resetTo(LoginRoute)
        assertEquals(listOf<NavKey>(LoginRoute), stack)
    }
}
