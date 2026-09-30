package dev.comon.fsp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.comon.fsp.core.navigation.AccountHomeRoute
import dev.comon.fsp.core.navigation.LoginRoute
import dev.comon.fsp.core.navigation.OfflineDashboardRoute
import dev.comon.fsp.core.navigation.StartupRoute
import dev.comon.fsp.feature.auth.LoginEntry
import dev.comon.fsp.feature.auth.StartupEntry
import dev.comon.fsp.feature.dashboard.AccountHomeEntry
import dev.comon.fsp.feature.dashboard.OfflineDashboardEntry

/**
 * Root back stack. Each entry gets its own saveable state and ViewModelStore, so feature ViewModels
 * are scoped to their entry and cleared when it is popped. Feature modules never reference each other;
 * navigation is wired here through callbacks. Sign-in and sign-out replace the whole stack. Content
 * slots exist so tests can verify restoration without the Hilt graph.
 */
@Composable
fun FspNavigation(
    modifier: Modifier = Modifier,
    startupContent: @Composable (onSignedOut: () -> Unit, onSignedIn: () -> Unit) -> Unit =
        { signedOut, signedIn -> StartupEntry(onSignedOut = signedOut, onSignedIn = signedIn) },
    loginContent: @Composable (openOfflineMode: () -> Unit, onSignedIn: () -> Unit) -> Unit =
        { openOffline, signedIn -> LoginEntry(onOpenOfflineMode = openOffline, onSignedIn = signedIn) },
    accountHomeContent: @Composable (onSignedOut: () -> Unit) -> Unit = { AccountHomeEntry(onSignedOut = it) },
    offlineDashboardContent: @Composable (navigateToLogin: () -> Unit) -> Unit =
        { OfflineDashboardEntry(onNavigateToLogin = it) },
) {
    val backStack = rememberNavBackStack(StartupRoute)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<StartupRoute> {
                startupContent({ backStack.resetTo(LoginRoute) }, { backStack.resetTo(AccountHomeRoute) })
            }
            entry<LoginRoute> {
                loginContent({ backStack.navigateTo(OfflineDashboardRoute) }, { backStack.resetTo(AccountHomeRoute) })
            }
            entry<AccountHomeRoute> { accountHomeContent { backStack.resetTo(LoginRoute) } }
            entry<OfflineDashboardRoute> { offlineDashboardContent { backStack.popTo(LoginRoute) } }
        },
    )
}
