package build.wallet.ui.components.button

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * [Button]s side by side in a list.
 * @param buttonContents specifies the list of buttons to be shown
 */
@Composable
fun RowOfButtons(
  modifier: Modifier = Modifier,
  buttonContents: ButtonContentsList,
  interButtonSpacing: Dp,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement =
      Arrangement.spacedBy(
        space = interButtonSpacing,
        alignment = Alignment.CenterHorizontally
      )
  ) {
    for (buttonContent in buttonContents.buttonContents) {
      buttonContent()
    }
  }
}

/**
 * A list of buttonContents that will be displayed in SplitButtons
 */
@Immutable
data class ButtonContentsList(
  val buttonContents: List<@Composable RowScope.() -> Unit>,
)
