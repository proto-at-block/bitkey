package build.wallet.ui.components.sheet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A [CompositionLocalProvider] that provides a [SheetCloser] to be used by the sheet content. Defaults
 * to a no-op implementation when not defined.
 */
val LocalSheetCloser = staticCompositionLocalOf { SheetCloser { } }

/**
 * An interface which defines an action to close the sheet.
 */
fun interface SheetCloser {
  suspend operator fun invoke()
}
