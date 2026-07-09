@file:Suppress("TooManyFunctions")

package build.wallet.ui.app.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel.Status.*
import build.wallet.statemachine.nfc.NfcHelpBodyModel
import build.wallet.ui.theme.Theme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun NfcScreenSearchingPreview() {
  PreviewWalletTheme {
    NfcScreenInternalAndroid(
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
  name = "NFC iOS Searching",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
fun NfcScreenSearchingIosPreview() {
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

@Preview(name = "Android")
@Composable
fun NfcScreenSearchingAndroidPreview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalAndroid(
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

@Preview(name = "Android Help")
@Composable
fun NfcHelpScreenAndroidPreview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcHelpBodyModel(onBack = {}, devicePlatform = Android).render(Modifier)
  }
}

@Preview
@Composable
fun NfcScreenConnectedPreview() {
  PreviewWalletTheme {
    NfcScreenInternalAndroid(
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
  name = "NFC iOS Connected",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
fun NfcScreenConnectedIosPreview() {
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

@Preview(name = "Android")
@Composable
fun NfcScreenConnectedAndroidPreview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalAndroid(
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
    NfcScreenInternalAndroid(
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
  name = "NFC iOS Connected With Spinner",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
fun NfcScreenConnectedWithSpinnerIosPreview() {
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

@Preview(name = "Android")
@Composable
fun NfcScreenConnectedWithSpinnerAndroidPreview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalAndroid(
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
    NfcScreenInternalAndroid(
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
  name = "NFC iOS Success",
  widthDp = 390,
  heightDp = 844,
  showBackground = true,
  backgroundColor = 0xFF000000
)
@Composable
fun NfcScreenSuccessIosPreview() {
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

@Preview(name = "Android")
@Composable
fun NfcScreenSuccessAndroidPreview() {
  PreviewWalletTheme(
    theme = Theme.DARK,
    backgroundColor = Color.Black,
  ) {
    NfcScreenInternalAndroid(
      model =
        NfcBodyModel(
          text = "Success",
          status = Success,
          eventTrackerScreenInfo = null
        )
    )
  }
}
