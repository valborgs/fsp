package dev.comon.fsp.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Typed back stack keys. Keys carry identifiers only; never put answers, tokens, or passwords here
 * because the back stack is written to saved instance state.
 */
/** App start: restores a stored account session (S01) and then replaces itself. */
@Serializable
data object StartupRoute : NavKey

@Serializable
data object LoginRoute : NavKey

/** Signed-in account home. The account is read from the session store, not carried in the key. */
@Serializable
data object AccountHomeRoute : NavKey

/** Anonymous (no account) interviewer workspace. Uses only surveys already stored on the device. */
@Serializable
data object OfflineDashboardRoute : NavKey
