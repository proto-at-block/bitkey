package bitkey.ui.framework

/**
 * A unique key for the screen, identifying the specific type of the screen.
 * Used by internal implementations to differentiate between different screens.
 */
internal val Screen.key: String get() = this::class.qualifiedName!!
