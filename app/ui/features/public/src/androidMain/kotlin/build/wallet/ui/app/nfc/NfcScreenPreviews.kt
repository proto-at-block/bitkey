@file:Suppress("TooManyFunctions")

package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel.Status.*
import build.wallet.statemachine.nfc.NfcHelpBodyModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun NfcScreenSearchingPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "Hold your Bitkey to the back of your phone",
          status = Searching { },
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(
  name = "NFC iOS DSV2 Searching",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
fun NfcScreenSearchingIosDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalIos(
      model =
        NfcBodyModel(
          text = "Hold your Bitkey to the back of your phone",
          status = Searching { },
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(name = "Android DSV2")
@Composable
fun NfcScreenSearchingAndroidDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalV2(
      model =
        NfcBodyModel(
          text = "Hold your Bitkey to the back of your phone",
          status = Searching { },
          onHelpClick = {},
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(name = "Android DSV2 Help")
@Composable
fun NfcHelpScreenAndroidDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcHelpBodyModel(onBack = {}).render(Modifier)
  }
}

@Preview
@Composable
fun NfcScreenConnectedPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "Hold your Bitkey to the back of your phone",
          status = Connected(onCancel = {}),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(
  name = "NFC iOS DSV2 Connected",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
fun NfcScreenConnectedIosDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalIos(
      model =
        NfcBodyModel(
          text = "Hold your Bitkey to the back of your phone",
          status = Connected(onCancel = {}),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(name = "Android DSV2")
@Composable
fun NfcScreenConnectedAndroidDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalV2(
      model =
        NfcBodyModel(
          text = "Hold your Bitkey to the back of your phone",
          status = Connected(onCancel = {}),
          onHelpClick = {},
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
fun NfcScreenConnectedWithSpinnerPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "This can take up to 1 minute…",
          status = Connected(onCancel = {}, showProgressSpinner = true),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(
  name = "NFC iOS DSV2 Connected With Spinner",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
fun NfcScreenConnectedWithSpinnerIosDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalIos(
      model =
        NfcBodyModel(
          text = "This can take up to 1 minute…",
          status = Connected(onCancel = {}, showProgressSpinner = true),
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(name = "Android DSV2")
@Composable
fun NfcScreenConnectedWithSpinnerAndroidDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalV2(
      model =
        NfcBodyModel(
          text = "This can take up to 1 minute…",
          status = Connected(onCancel = {}, showProgressSpinner = true),
          onHelpClick = {},
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
fun NfcScreenSuccessPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "Success",
          status = Success,
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(
  name = "NFC iOS DSV2 Success",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
fun NfcScreenSuccessIosDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalIos(
      model =
        NfcBodyModel(
          text = "Success",
          status = Success,
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview(name = "Android DSV2")
@Composable
fun NfcScreenSuccessAndroidDsv2Preview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalV2(
      model =
        NfcBodyModel(
          text = "Success",
          status = Success,
          eventTrackerScreenInfo = null
        )
    )
  }
}
