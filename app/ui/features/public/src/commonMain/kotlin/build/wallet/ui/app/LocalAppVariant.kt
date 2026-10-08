package build.wallet.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import build.wallet.platform.config.AppVariant

/**
 * The [AppVariant] of the running app, for purely cosmetic per-variant UI (e.g. [StatusBarGutter]).
 *
 * Defaults to [AppVariant.Customer] so anything that isn't explicitly provided a variant gets the
 * production look. Do not use this to gate features; inject [AppVariant] into state machines instead.
 */
val LocalAppVariant = staticCompositionLocalOf { AppVariant.Customer }
