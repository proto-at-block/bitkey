package build.wallet.ui.components.button

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import build.wallet.ui.model.button.ButtonModel

@Composable
fun OrderedButtonPair(
  primary: ButtonModel?,
  secondary: ButtonModel?,
  spacing: Dp,
  modifier: Modifier = Modifier,
  renderButton: @Composable (ButtonModel) -> Unit = { Button(model = it) },
) {
  Column(modifier = modifier) {
    primary?.let { button ->
      renderButton(button)
      secondary?.let {
        Spacer(modifier = Modifier.height(spacing))
      }
    }
    secondary?.let { button ->
      renderButton(button)
    }
  }
}
