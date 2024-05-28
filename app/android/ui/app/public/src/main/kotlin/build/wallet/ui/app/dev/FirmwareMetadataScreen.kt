package build.wallet.ui.app.dev

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.dev.FirmwareMetadataBodyModel
import build.wallet.statemachine.dev.FirmwareMetadataModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun FirmwareMetadataScreen(model: FirmwareMetadataBodyModel) {
  BackHandler(onBack = model.onBack)

  Column(
    modifier =
      Modifier
        .background(WalletTheme.colors.background)
        .fillMaxSize()
        .padding(horizontal = 20.dp)
  ) {
    Toolbar(
      model =
        ToolbarModel(
          leadingAccessory = BackAccessory(onClick = model.onBack),
          middleAccessory = ToolbarMiddleAccessoryModel(title = "Firmware Metadata")
        )
    )

    Spacer(Modifier.height(24.dp))
    Button(
      model = BitkeyInteractionButtonModel(
        text = "Refresh Metadata",
        size = Footer,
        onClick = StandardClick(model.onFirmwareMetadataRefreshClick)
      )
    )

    model.firmwareMetadataModel?.let { metadata ->
      Spacer(Modifier.height(24.dp))
      Card {
        ListItem(
          title = "Active Slot",
          sideText = metadata.activeSlot
        )

        Divider()
        ListItem(
          title = "Git ID",
          sideText = metadata.gitId
        )

        Divider()
        ListItem(
          title = "Git Branch",
          sideText = metadata.gitBranch
        )

        Divider()
        ListItem(
          title = "Version",
          sideText = metadata.version
        )

        Divider()
        ListItem(
          title = "Build",
          sideText = metadata.build
        )

        Divider()
        ListItem(
          title = "Timestamp",
          sideText = metadata.timestamp
        )

        Divider()
        ListItem(
          title = "Hash",
          sideText = metadata.hash
        )

        Divider()
        ListItem(
          title = "HW Revision",
          sideText = metadata.hwRevision
        )
      }
    }
  }
}

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
