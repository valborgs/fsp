package dev.comon.fsp.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Typed back stack keys. Keys carry identifiers only; never put answers, tokens, or passwords here
 * because the back stack is written to saved instance state.
 */
@Serializable
data object LoginRoute : NavKey

/** Anonymous (no account) interviewer workspace. Uses only surveys already stored on the device. */
@Serializable
data object OfflineDashboardRoute : NavKey
