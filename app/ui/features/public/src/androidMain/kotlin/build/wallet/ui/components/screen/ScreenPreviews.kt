package build.wallet.ui.components.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.status.StatusBannerModel
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun ScreenWithBodyOnlyPreview() {
  PreviewWalletTheme {
    Screen(
      bodyContent = {
        BodyContentForPreview()
      }
    )
  }
}

@Preview
@Composable
fun ScreenWithBodyAndAlertPreview() {
  PreviewWalletTheme {
    Screen(
      bodyContent = {
        BodyContentForPreview()
      },
      alertModel =
        ButtonAlertModel(
          title = "Alert Alert Alert",
          subline = "This is an alert.",
          onDismiss = {},
          primaryButtonText = "Primary",
          onPrimaryButtonClick = {},
          primaryButtonStyle = ButtonAlertModel.ButtonStyle.Destructive,
          secondaryButtonText = "Secondary",
          onSecondaryButtonClick = {}
        )
    )
  }
}

@Preview
@Composable
fun ScreenWithBodyAndStatusBannerPreview() {
  PreviewWalletTheme {
    Screen(
      model = ScreenModel(
        body = object : BodyModel() {
          override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null

          @Composable
          override fun render(modifier: Modifier) {
            BodyContentForPreview()
          }
        },
        statusBannerModel = StatusBannerModel(
          title = "Title",
          subtitle = "Subtitle",
          onClick = null
        )
      )
    )
  }
}

@Composable
private fun BodyContentForPreview() {
  Box(
    modifier =
      Modifier
        .background(Color.Red)
        .fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Label(text = "Body Content", type = LabelType.Title2)
  }
}
