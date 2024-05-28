package build.wallet.statemachine

import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.framework.Screen

data class NavigatingBodyModelFake(
  val id: String,
  val goTo: (Screen) -> Unit,
) : BodyModel() {
  override val eventTrackerScreenInfo = null
}
