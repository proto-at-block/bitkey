package bitkey.ui.framework

/**
 * A unique key for the screen, identifying the specific type of the screen.
 * Used by internal implementations to differentiate between different screens.
 */
internal val Screen.key: String
  get() = checkNotNull(this::class.qualifiedName) {
    "Screen implementations must not be anonymous or local classes."
  }

/**
 * A unique key for the sheet, identifying the specific type of the sheet.
 * Used by internal implementations to differentiate between different sheets.
 */
internal val Sheet.key: String
  get() = checkNotNull(this::class.qualifiedName) {
    "Sheet implementations must not be anonymous or local classes."
  }
