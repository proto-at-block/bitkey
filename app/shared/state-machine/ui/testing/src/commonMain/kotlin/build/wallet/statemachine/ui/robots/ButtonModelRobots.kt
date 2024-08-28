package build.wallet.statemachine.ui.robots

import build.wallet.statemachine.ui.matchers.shouldBeEnabled
import build.wallet.statemachine.ui.matchers.shouldNotBeLoading
import build.wallet.ui.model.button.ButtonModel
import io.kotest.matchers.nulls.shouldNotBeNull

/**
 * Clicks this [ButtonModel], expecting it to not be null, be enabled and not loading.
 */
fun ButtonModel?.click() {
  shouldNotBeNull()
  shouldBeEnabled()
  shouldNotBeLoading()
  onClick()
}
