package build.wallet.ui.components.button

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled

@Composable
fun OrderedButtonPair(
  primary: ButtonModel?,
  secondary: ButtonModel?,
  spacing: Dp,
  modifier: Modifier = Modifier,
  renderButton: @Composable (ButtonModel) -> Unit = { Button(model = it) },
) {
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val topButton = if (isDesignSystemV2Enabled) primary else secondary
  val bottomButton = if (isDesignSystemV2Enabled) secondary else primary

  Column(modifier = modifier) {
    topButton?.let { button ->
      renderButton(button)
      bottomButton?.let {
        Spacer(modifier = Modifier.height(spacing))
      }
    }
    bottomButton?.let { button ->
      renderButton(button)
    }
  }
}
