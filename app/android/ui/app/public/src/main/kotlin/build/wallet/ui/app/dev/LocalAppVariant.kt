package build.wallet.ui.app.dev

import androidx.compose.runtime.staticCompositionLocalOf
import build.wallet.platform.config.AppVariant

/**
 * Local composition local for [AppVariant]. This should be provided at the top-level of the app.
 */
val LocalAppVariant = staticCompositionLocalOf<AppVariant?> { null }
