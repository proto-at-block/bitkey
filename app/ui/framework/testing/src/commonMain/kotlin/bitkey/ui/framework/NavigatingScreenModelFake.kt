package bitkey.ui.framework

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.statemachine.core.BodyModel

data class NavigatingBodyModelFake(
  val id: String,
  val goTo: (Screen) -> Unit,
  val showSheet: (Sheet) -> Unit,
  val closeSheet: () -> Unit,
) : BodyModel() {
  override val eventTrackerScreenInfo = null

  @Composable
  override fun render(modifier: Modifier) {
    Text(this.toString())
  }
}
