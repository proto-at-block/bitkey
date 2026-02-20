package build.wallet.ui.app.dev

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.dev.FirmwareMetadataBodyModel
import build.wallet.statemachine.dev.FirmwareMetadataModel
import build.wallet.statemachine.dev.McuInfoModel
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
fun FirmwareMetadataWithMultipleMcusPreview() {
  PreviewWalletTheme {
    FirmwareMetadataScreen(
      model =
        FirmwareMetadataBodyModel(
          onBack = { },
          onFirmwareMetadataRefreshClick = { },
          firmwareMetadataModel =
            FirmwareMetadataModel(
              activeSlot = "A",
              gitId = "abc123def",
              gitBranch = "main",
              version = "2.0.1",
              build = "prod",
              timestamp = "1/15/2026, 10:30:00 AM PST",
              hash = "FEDCBA9876543210",
              hwRevision = "w3a-evt",
              mcuInfo = listOf(
                McuInfoModel(
                  role = "CORE",
                  name = "EFR32",
                  firmwareVersion = "2.0.1"
                ),
                McuInfoModel(
                  role = "UXC",
                  name = "STM32U5",
                  firmwareVersion = "1.5.0"
                )
              )
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
