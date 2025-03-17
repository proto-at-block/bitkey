package build.wallet.ui.model.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import build.wallet.ui.components.tab.CircularTabRow
import build.wallet.ui.model.ComposeModel

@Immutable
data class CircularTabRowModel(
  val items: List<String>,
  val selectedItemIndex: Int = 0,
  val onClick: (index: Int) -> Unit = {},
  override val key: String,
) : ComposeModel {
  @Composable
  override fun render(modifier: Modifier) {
    CircularTabRow(modifier, model = this)
  }
}
