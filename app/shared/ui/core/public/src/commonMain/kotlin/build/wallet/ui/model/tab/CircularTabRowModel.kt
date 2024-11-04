package build.wallet.ui.model.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import build.wallet.statemachine.core.ComposableRenderedModel
import build.wallet.ui.components.tab.CircularTabRow

@Immutable
data class CircularTabRowModel(
  val items: List<String>,
  val selectedItemIndex: Int = 0,
  val onClick: (index: Int) -> Unit = {},
  override val key: String,
) : ComposableRenderedModel {
  @Composable
  override fun render(modifier: Modifier) {
    CircularTabRow(model = this)
  }
}
