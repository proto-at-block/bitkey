package build.wallet.statemachine.send

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.ui.app.qrcode.DynamicIslandQrScannerPortalScreen
import build.wallet.ui.components.screen.Screen
import build.wallet.ui.model.render

data class DynamicIslandQrScannerPortalBodyModel(
  val recipientAddressBodyModel: BodyModel,
  val qrScannerScreenModel: ScreenModel?,
  val isClosing: Boolean,
  val onClose: () -> Unit,
  val onClosed: () -> Unit,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?
    get() = recipientAddressBodyModel.eventTrackerScreenInfo

  @Composable
  override fun render(modifier: Modifier) {
    val qrScannerBodyModel = qrScannerScreenModel?.body
    val portalScannerBodyModel = qrScannerBodyModel as? QrCodeScanBodyModel
    Box(modifier = modifier.fillMaxSize()) {
      if (portalScannerBodyModel != null || isClosing) {
        DynamicIslandQrScannerPortalScreen(
          modifier = Modifier.fillMaxSize(),
          content = {
            RecipientAddressContent()
          },
          model = portalScannerBodyModel,
          isClosing = isClosing,
          onClose = onClose,
          onClosed = onClosed
        )
      } else {
        RecipientAddressContent()
        qrScannerScreenModel?.let { scannerScreenModel ->
          Screen(
            modifier = Modifier.fillMaxSize(),
            model = scannerScreenModel
          )
        }
      }
    }
  }

  @Composable
  private fun RecipientAddressContent() {
    val density = LocalDensity.current
    val statusBars = WindowInsets.statusBars
    val navigationBars = WindowInsets.navigationBars
    val statusBarTopPadding = remember(density) {
      with(density) { statusBars.getTop(this).toDp() }
    }
    val navigationBarBottomPadding = remember(density) {
      with(density) { navigationBars.getBottom(this).toDp() }
    }

    recipientAddressBodyModel.render(
      Modifier
        .fillMaxSize()
        .padding(
          top = statusBarTopPadding,
          bottom = navigationBarBottomPadding
        )
    )
  }
}
