package build.wallet.statemachine.settings

import build.wallet.platform.config.AppVariant

/**
 * Determines if this [AppVariant] should show the debug menu.
 */
internal val AppVariant.showDebugMenu: Boolean
  get() = this == AppVariant.Development || this == AppVariant.Team
