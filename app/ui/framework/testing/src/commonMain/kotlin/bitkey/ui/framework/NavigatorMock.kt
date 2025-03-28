package bitkey.ui.framework

import app.cash.turbine.Turbine

/**
 * Mock implementation of [Navigator] used primarily by [ScreenPresenter.test] to track
 * and deterministically validate navigation events using [app.cash.turbine.Turbine].
 */
class NavigatorMock(
  turbine: (String) -> Turbine<Any>,
) : Navigator {
  val goToCalls = turbine("Navigator.goTo")
  val exitCalls = turbine("Navigator.exit")

  override fun goTo(screen: Screen) {
    goToCalls.add(screen)
  }

  override fun exit() {
    exitCalls.add(Unit)
  }
}
