package build.wallet.statemachine.send

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.BodyModelScreenStyle
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
  // Reserve the system bar insets like the recipient form does, so the screen container doesn't
  // animate its status bar inset in or out when the portal hands off to (or takes over from) the
  // form. The portal draws back over the reserved insets in [render].
  override val screenStyle: BodyModelScreenStyle =
    BodyModelScreenStyle(
      requiresSystemBarsPadding = true,
      drawsBehindReservedStatusBar = true
    )

  override val eventTrackerScreenInfo: EventTrackerScreenInfo?
    get() = recipientAddressBodyModel.eventTrackerScreenInfo

  @Composable
  override fun render(modifier: Modifier) {
    val qrScannerBodyModel = qrScannerScreenModel?.body
    val portalScannerBodyModel = qrScannerBodyModel as? QrCodeScanBodyModel
    val density = LocalDensity.current
    val statusBarTopPx = WindowInsets.statusBars.getTop(density)
    val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          // Let the box grow past the padded container so it covers the reserved insets.
          .wrapContentHeight(align = Alignment.Top, unbounded = true)
          .height(maxHeight + with(density) { (statusBarTopPx + navigationBarBottomPx).toDp() })
          .offset { IntOffset(x = 0, y = -statusBarTopPx) }
      ) {
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
          Box(
            modifier = Modifier
              .fillMaxSize()
              // A scanner error screen covers the recipient form, so keep its controls away from
              // accessibility services until the error is dismissed.
              .then(if (qrScannerScreenModel != null) Modifier.clearAndSetSemantics {} else Modifier)
          ) {
            RecipientAddressContent()
          }
        }
      }

      if (portalScannerBodyModel == null && !isClosing) {
        qrScannerScreenModel?.let { scannerScreenModel ->
          // Scanner error screens pad themselves for the navigation bar, but the screen container
          // has already consumed that inset, so their own padding is zero. Extend them over the
          // status bar only, leaving them above the container's navigation bar padding.
          Screen(
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentHeight(align = Alignment.Top, unbounded = true)
              .height(maxHeight + with(density) { statusBarTopPx.toDp() })
              .offset { IntOffset(x = 0, y = -statusBarTopPx) },
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
