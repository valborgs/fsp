package dev.comon.fsp.navigation

import androidx.navigation3.runtime.NavKey

/** Pushes [key] unless it is already on top, so a double tap cannot stack duplicate screens. */
internal fun MutableList<NavKey>.navigateTo(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/**
 * Removes every entry above [root]. If [root] is missing, the stack becomes just [root].
 * Never leaves the stack empty, which NavDisplay does not allow.
 */
internal fun MutableList<NavKey>.popTo(root: NavKey) {
    val index = indexOf(root)
    if (index < 0) {
        resetTo(root)
    } else {
        while (size > index + 1) removeAt(lastIndex)
    }
}

/**
 * Makes [key] the only entry: sign-in and sign-out boundaries, so Back never returns to the login
 * screen from an account (or into an account after logout). Adds before removing, never empty.
 */
internal fun MutableList<NavKey>.resetTo(key: NavKey) {
    if (size == 1 && first() == key) return
    add(key)
    while (size > 1) removeAt(0)
}
