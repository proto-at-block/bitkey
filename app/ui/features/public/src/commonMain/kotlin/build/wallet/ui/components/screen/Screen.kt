package build.wallet.ui.components.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
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
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.SystemUIModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.ui.app.effectiveTheme
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
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.LocalTheme
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
  val appTheme = LocalTheme.current
  val screenTheme = effectiveTheme(appTheme = appTheme, screenThemePreference = model.themePreference)

  CompositionLocalProvider(LocalTheme provides screenTheme) {
    WalletTheme {
      ScreenTheme(
        model.body,
        model.presentationStyle,
        hasStatusBanner = model.statusBannerModel != null
      ) { style ->
        val density = LocalDensity.current

        val statusBannerModel by produceState(model.statusBannerModel, model) {
          value = model.statusBannerModel ?: value
        }

        val statusBannerVisible = remember(model.statusBannerModel) {
          model.statusBannerModel != null
        }

        val systemStatusBarHeightPx = with(density) {
          WindowInsets.statusBars.getTop(this)
        }
        val shouldReserveStatusBarInset = remember(model.body, model.presentationStyle, statusBannerVisible, style) {
          shouldReserveStatusBarInset(
            body = model.body,
            presentationStyle = model.presentationStyle,
            statusBannerVisible = statusBannerVisible,
            addSystemBarsPadding = style.addSystemBarsPadding
          )
        }

        val statusBannerAlpha by animateFloatAsState(
          targetValue = statusBannerAlpha(statusBannerVisible),
          label = "status-banner-alpha"
        )

        val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
        val borderRadius by animateDpAsState(
          targetValue = statusBannerBorderRadius(
            statusBannerVisible = statusBannerVisible,
            isDesignSystemV2Enabled = isDesignSystemV2Enabled
          ),
          label = "status-banner-border-radius",
          animationSpec = tween(
            durationMillis = 300
          )
        )

        val backgroundColor by animateColorAsState(
          targetValue =
            model.statusBannerModel?.backgroundColor() ?: WalletTheme.colors.background,
          label = "screen-background-color",
          animationSpec = screenBackgroundAnimationSpec(statusBannerVisible)
        )

        Column(
          modifier = modifier.background(
            color = backgroundColor
          ),
          verticalArrangement = Arrangement.Top
        ) {
          Box(
            modifier = Modifier
              .background(color = style.statusBarColor)
              .thenIf(statusBannerVisible.not()) {
                Modifier.alpha(statusBannerAlpha)
              }
              // fill width first to prevent horizontal size animation
              .fillMaxWidth()
              .animateContentSize()
              .thenIf(shouldReserveStatusBarInset) {
                Modifier.heightIn(
                  min = with(density) { systemStatusBarHeightPx.toDp() }
                )
              }
              .height(statusBannerHeight(statusBannerVisible)),
            contentAlignment = Alignment.TopCenter
          ) {
            statusBannerModel?.render(
              modifier = Modifier
                // unbounded to avoid immediate height change to zero
                .wrapContentHeight(Alignment.Top, unbounded = true)
            )
          }

          val timeoutToastModel by rememberTimeoutToastModel(model.toastModel)

          val addSystemBarsPadding = remember(model, style, timeoutToastModel) {
            navigationBarsPaddingModifier(
              addSystemBarsPadding = style.addSystemBarsPadding,
              timeoutToastModel = timeoutToastModel
            )
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
            backgroundColor = style.screenBackgroundColor,
            alertModel = model.alertModel,
            toastModel = timeoutToastModel,
            bottomSheetModel = model.bottomSheetModel,
            onTwoFingerDoubleTap = model.onTwoFingerDoubleTap,
            systemUiModel = model.systemUIModel
          )
        }
      }
    }
  }
}

private fun shouldReserveStatusBarInset(
  body: Any,
  presentationStyle: ScreenPresentationStyle,
  statusBannerVisible: Boolean,
  addSystemBarsPadding: Boolean,
): Boolean {
  val isHomeOrSecurityHubRootFullScreen =
    presentationStyle == ScreenPresentationStyle.RootFullScreen &&
      (body is MoneyHomeBodyModel || body is SecurityHubBodyModel)

  return addSystemBarsPadding ||
    // Keep previous behavior for non-root-full-screen presentations.
    (statusBannerVisible && presentationStyle != ScreenPresentationStyle.RootFullScreen) ||
    // For Money Home / Security Hub RootFullScreen, reserve inset only when no banner is
    // visible; banners already handle status bar inset internally.
    (isHomeOrSecurityHubRootFullScreen && statusBannerVisible.not())
}

private fun statusBannerAlpha(statusBannerVisible: Boolean): Float {
  return if (statusBannerVisible) 1f else 0f
}

private fun statusBannerBorderRadius(
  statusBannerVisible: Boolean,
  isDesignSystemV2Enabled: Boolean,
): Dp {
  return if (statusBannerVisible) {
    if (isDesignSystemV2Enabled) 32.dp else 24.dp
  } else {
    0.dp
  }
}

private fun screenBackgroundAnimationSpec(statusBannerVisible: Boolean) =
  if (statusBannerVisible) {
    tween<Color>(
      durationMillis = 300
    )
  } else {
    tween<Color>(
      durationMillis = 300,
      delayMillis = 300 // Delay the exit animation to allow the status banner to fade out first
    )
  }

private fun statusBannerHeight(statusBannerVisible: Boolean): Dp {
  return if (statusBannerVisible) Dp.Unspecified else 0.dp
}

@Composable
private fun rememberTimeoutToastModel(toastModel: ToastModel?): State<ToastModel?> {
  return produceState(toastModel, toastModel) {
    if (toastModel == null) {
      value = null
      return@produceState
    }
    value = toastModel
    delay(2.5.seconds)
    value = null
  }
}

private fun navigationBarsPaddingModifier(
  addSystemBarsPadding: Boolean,
  timeoutToastModel: ToastModel?,
): Modifier {
  return if (addSystemBarsPadding && timeoutToastModel == null) {
    Modifier.navigationBarsPadding()
  } else {
    Modifier
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
  backgroundColor: Color = WalletTheme.colors.background,
  toastModel: ToastModel? = null,
  alertModel: AlertModel? = null,
  bottomSheetModel: SheetModel? = null,
  systemUiModel: SystemUIModel? = null,
  onTwoFingerDoubleTap: (() -> Unit)? = null,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var displayedSheetModel by remember { mutableStateOf<SheetModel?>(null) }

  LaunchedEffect(bottomSheetModel) {
    if (bottomSheetModel != null) {
      displayedSheetModel = bottomSheetModel
    } else if (displayedSheetModel != null) {
      if (sheetState.isVisible) {
        sheetState.hide()
      }
      displayedSheetModel = null
    }
  }

  ScreenContents(
    modifier = modifier,
    bodyContent = bodyContent,
    backgroundColor = backgroundColor,
    onTwoFingerDoubleTap = onTwoFingerDoubleTap
  )

  // Add alert bottom sheet, system UI, toast if any
  alertModel?.let {
    AlertDialog(model = it)
  }

  displayedSheetModel?.let {
    Sheet(
      model = it,
      sheetState = sheetState
    )
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
  backgroundColor: Color = WalletTheme.colors.background,
  onTwoFingerDoubleTap: (() -> Unit)? = null,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(color = backgroundColor)
      .onTwoFingerDoubleTap {
        onTwoFingerDoubleTap?.invoke()
      }
  ) {
    bodyContent()
  }
}
