package bitkey.ui.framework

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign

/**
 * Mock implementation of [Navigator] used primarily by [ScreenPresenter.test] to track
 * and deterministically validate navigation events using [app.cash.turbine.Turbine].
 */
class NavigatorMock(
  turbine: (String) -> Turbine<Any>,
) : Navigator {
  val goToCalls = turbine("Navigator.goTo")
  val exitCalls = turbine("Navigator.exit")
  val showSheetCalls = turbine("Navigator.showSheet")
  val closeSheetCalls = turbine("Navigator.closeSheet")

  override fun goTo(screen: Screen) {
    goToCalls.add(screen)
  }

  override fun showSheet(sheet: Sheet) {
    showSheetCalls += sheet
  }

  override fun closeSheet() {
    closeSheetCalls += Unit
  }

  override fun exit() {
    exitCalls.add(Unit)
  }
}
