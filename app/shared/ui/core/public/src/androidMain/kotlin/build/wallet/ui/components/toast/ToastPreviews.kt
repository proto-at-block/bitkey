package build.wallet.ui.components.toast

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toast.ToastModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
internal fun ToastWithStrokeColorPreview() {
  PreviewWalletTheme {
    Column {
      ToastComposable(
        model = ToastModel(
          leadingIcon = IconModel(
            icon = Icon.SmallIconCheckFilled,
            iconSize = IconSize.Accessory,
            iconTint = IconTint.Success
          ),
          title = "This is a toast",
          iconStrokeColor = ToastModel.IconStrokeColor.White
        )
      )
    }
  }
}

@Preview
@Composable
internal fun ToastPreview() {
  PreviewWalletTheme {
    Column {
      ToastComposable(
        model = ToastModel(
          leadingIcon = IconModel(
            icon = Icon.SmallIconCheckFilled,
            iconSize = IconSize.Accessory,
            iconTint = IconTint.Primary
          ),
          title = "This is a toast",
          iconStrokeColor = ToastModel.IconStrokeColor.Unspecified
        )
      )
    }
  }
}
