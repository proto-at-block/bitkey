package build.wallet.ui.components.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.status.BannerStyle
import build.wallet.ui.model.status.StatusBannerModel
import build.wallet.ui.tokens.LabelType
import io.kotest.core.spec.style.FunSpec

class ScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension(DeviceConfig.PIXEL_6)

  test("screen with body only") {
    paparazzi.snapshot {
      ScreenWithBodyOnlyPreview()
    }
  }

  test("screen with alert") {
    paparazzi.snapshot {
      ScreenWithBodyAndAlertPreview()
    }
  }

  test("screen with status banner and design system v2 feature flag off") {
    paparazzi.snapshot {
      ScreenWithBodyAndStatusBannerPreview()
    }
  }

  test("screen with status banner and design system v2 feature flag on") {
    paparazzi.snapshot(designSystemUpdatesEnabled = true) {
      ScreenWithBodyAndStatusBannerForSnapshot()
    }
  }
})

@Composable
private fun ScreenWithBodyAndStatusBannerForSnapshot() {
  Screen(
    model = ScreenModel(
      body = object : BodyModel() {
        override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null

        @Composable
        override fun render(modifier: Modifier) {
          BodyContentForSnapshot()
        }
      },
      statusBannerModel = StatusBannerModel(
        title = "Title",
        subtitle = "Subtitle",
        style = BannerStyle.Warning,
        onClick = null
      )
    )
  )
}

@Composable
private fun BodyContentForSnapshot() {
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
