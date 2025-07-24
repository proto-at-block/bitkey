package build.wallet.ui.components.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SystemUIModel
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.sheet.Sheet
import build.wallet.ui.components.status.backgroundColor
import build.wallet.ui.components.system.SystemUI
import build.wallet.ui.components.toast.Toast
import build.wallet.ui.compose.gestures.onTwoFingerDoubleTap
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.alert.AlertModel
import build.wallet.ui.model.render
import build.wallet.ui.model.toast.ToastModel
import build.wallet.ui.theme.WalletTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Defines UI scaffold layout for any screen in the app. Data and style rendered on the screen is
 * defined by its [model].
 */
@Composable
fun Screen(
  modifier: Modifier = Modifier,
  model: ScreenModel,
) {
  ScreenTheme(
    model.body,
    model.presentationStyle
  ) { style ->
    Column(
      modifier = modifier.background(
        color = model.statusBannerModel?.backgroundColor() ?: WalletTheme.colors.background
      ),
      verticalArrangement = Arrangement.Top
    ) {
      val statusBannerModel by produceState(model.statusBannerModel, model) {
        value = model.statusBannerModel ?: value
      }
      val statusBannerVisible = remember(model.statusBannerModel) {
        model.statusBannerModel != null
      }

      val statusBannerAlpha by animateFloatAsState(
        targetValue = if (statusBannerVisible) 1f else 0f,
        label = "status-banner-alpha"
      )
      val density = LocalDensity.current
      val systemStatusBarHeightPx = with(density) {
        WindowInsets.statusBars.getTop(this)
      }
      val borderRadius by animateDpAsState(
        targetValue = if (statusBannerVisible) 24.dp else 0.dp,
        label = "status-banner-border-radius"
      )

      Box(
        modifier = Modifier
          .background(color = style.statusBarColor)
          .thenIf(statusBannerVisible.not()) {
            Modifier.alpha(statusBannerAlpha)
          }
          // fill width first to prevent horizontal size animation
          .fillMaxWidth()
          .animateContentSize()
          .thenIf(statusBannerVisible || style.addSystemBarsPadding) {
            Modifier.heightIn(
              min = with(density) { systemStatusBarHeightPx.toDp() }
            )
          }
          .height(if (statusBannerVisible) Dp.Unspecified else 0.dp),
        contentAlignment = Alignment.TopCenter
      ) {
        statusBannerModel?.render(
          modifier = Modifier
            // unbounded to avoid immediate height change to zero
            .wrapContentHeight(Alignment.Top, unbounded = true)
        )
      }

      val timeoutToastModel by produceState(model.toastModel, model.toastModel) {
        if (model.toastModel == null) {
          value = null
          return@produceState
        }
        value = model.toastModel
        delay(2.5.seconds)
        value = null
      }

      val addSystemBarsPadding = remember(model, style, timeoutToastModel) {
        when {
          style.addSystemBarsPadding && timeoutToastModel == null -> Modifier.navigationBarsPadding()
          else -> Modifier
        }
      }
      Screen(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(topStart = borderRadius, topEnd = borderRadius))
          .background(style.screenBackgroundColor)
          .then(addSystemBarsPadding),
        bodyContent = {
          model.body.render()
        },
        alertModel = model.alertModel,
        toastModel = timeoutToastModel,
        bottomSheetModel = model.bottomSheetModel,
        onTwoFingerDoubleTap = model.onTwoFingerDoubleTap,
        systemUiModel = model.systemUIModel
      )
    }
  }
}

/**
 * Defines UI scaffold layout for any screen in the app. Data and style rendered on the screen is
 * defined by the content used to build a screen.
 */
@Composable
internal fun Screen(
  modifier: Modifier = Modifier,
  bodyContent: @Composable () -> Unit,
  toastModel: ToastModel? = null,
  alertModel: AlertModel? = null,
  bottomSheetModel: SheetModel? = null,
  systemUiModel: SystemUIModel? = null,
  onTwoFingerDoubleTap: (() -> Unit)? = null,
) {
  @Composable
  fun ScreenContents() =
    ScreenContents(
      modifier = modifier,
      bodyContent = bodyContent,
      onTwoFingerDoubleTap = onTwoFingerDoubleTap
    )

  if (alertModel == null) {
    ScreenContents()
  } else {
    // Wrap ScreenContents in Surface with AlertDialog if
    // alertModel is nonnull
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .alpha(0.5f),
      color = Color.Black
    ) {
      AlertDialog(model = alertModel)
      ScreenContents()
    }
  }

  // Add bottom sheet, system UI, toast if any
  bottomSheetModel?.let {
    Sheet(model = it)
  }
  systemUiModel?.let {
    SystemUI(model = it)
  }

  Toast(model = toastModel)
}

@Composable
private fun ScreenContents(
  modifier: Modifier = Modifier,
  bodyContent: @Composable () -> Unit,
  onTwoFingerDoubleTap: (() -> Unit)? = null,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(color = WalletTheme.colors.background)
      .onTwoFingerDoubleTap {
        onTwoFingerDoubleTap?.invoke()
      }
  ) {
    bodyContent()
  }
}
