package build.wallet.ui.app.dev

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.dev.FirmwareMetadataBodyModel
import build.wallet.statemachine.dev.FirmwareMetadataModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun FirmwareMetadataWithDataPreview() {
  PreviewWalletTheme {
    FirmwareMetadataScreen(
      model =
        FirmwareMetadataBodyModel(
          onBack = { },
          onFirmwareMetadataRefreshClick = { },
          firmwareMetadataModel =
            FirmwareMetadataModel(
              activeSlot = "A",
              gitId = "some-fake-id",
              gitBranch = "main",
              version = "1.0",
              build = "mock",
              timestamp = "3/06/2023, 3:38:05 PM PST",
              hash = "0123456789ABCDEF",
              hwRevision = "mocky-mcmockface :)"
            )
        )
    )
  }
}

@Preview
@Composable
fun FirmwareMetadataWithoutDataPreview() {
  PreviewWalletTheme {
    FirmwareMetadataScreen(
      model =
        FirmwareMetadataBodyModel(
          onBack = { },
          onFirmwareMetadataRefreshClick = { },
          firmwareMetadataModel = null
        )
    )
  }
}
