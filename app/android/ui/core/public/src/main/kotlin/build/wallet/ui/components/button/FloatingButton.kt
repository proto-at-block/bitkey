package build.wallet.ui.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.FloatingActionButtonModel
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.button.ButtonModel.Size.Floating
import build.wallet.ui.model.button.ButtonModel.Treatment.Black
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun FloatingButton(
  modifier: Modifier = Modifier,
  model: FloatingActionButtonModel,
) {
  Button(
    modifier = modifier,
    leadingIcon = model.icon,
    text = model.text,
    style =
      WalletTheme.buttonStyle(
        treatment = Black,
        size = Floating,
        cornerRadius = 52.dp,
        enabled = true
      ).copy(horizontalPadding = 22.dp, verticalPadding = 20.dp),
    onClick = model.onClick
  )
}

@Preview
@Composable
internal fun FloatingButtonPreview() {
  PreviewWalletTheme {
    Box(
      modifier =
        Modifier
          .padding(20.dp)
          .background(WalletTheme.colors.background)
    ) {
      FloatingButton(
        model =
          FloatingActionButtonModel(
            text = "Pay",
            icon = Icon.SmallIconScan,
            onClick = {}
          )
      )
    }
  }
}
