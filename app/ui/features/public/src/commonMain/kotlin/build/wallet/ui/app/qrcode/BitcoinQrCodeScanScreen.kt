package build.wallet.ui.app.qrcode

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.send.QrCodeScanBodyModel
import build.wallet.ui.components.button.OrderedButtonPair
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Unspecified
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.toolbar.ToolbarAccessory
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

private val qrCodeViewfinderMargin = 48.dp
private val qrCodeViewfinderBorderRadius = 40.dp
private val dynamicIslandIntroWidth = 120.dp
private val dynamicIslandIntroHeight = 36.dp
private val dynamicIslandFallbackTopMargin = 11.dp
private val dynamicIslandReferenceSafeAreaTop = 59.dp
private val dynamicIslandPortalEdgeMargin = 15.dp
private val dynamicIslandPortalBorderRadius = 40.dp
private val dynamicIslandEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private const val DYNAMIC_ISLAND_OPEN_DURATION_MILLIS = 460
private const val DYNAMIC_ISLAND_CLOSE_DURATION_MILLIS = 340
private const val DYNAMIC_ISLAND_SCANNER_FADE_START_PROGRESS = 0.12f
private const val DYNAMIC_ISLAND_INTRO_LABEL_FADE_END_PROGRESS = 0.42f
private const val DYNAMIC_ISLAND_SCAN_PROMPT = "Scan a QR code"

@Composable
fun QrCodeScanScreen(
  modifier: Modifier = Modifier,
  model: QrCodeScanBodyModel,
  scannerToolbarAlpha: Float = 1f,
  applySystemBarsPadding: Boolean = true,
) {
  val viewfinderStrokeColor by animateColorAsState(
    targetValue = if (model.isScanSuccess) WalletTheme.colors.positiveForeground else Color.White,
    label = "qr-code-scan-viewfinder-stroke-color"
  )

  BackHandler(onBack = model.onClose)
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    NativeQrCodeScanner(model = model)
    QrCodeScanViewFinder(strokeColor = viewfinderStrokeColor)
    QrCodeScanWidgets(
      model = model,
      scannerToolbarAlpha = scannerToolbarAlpha,
      applySystemBarsPadding = applySystemBarsPadding
    )
  }
}

@Composable
fun DynamicIslandQrScannerPortalScreen(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
  model: QrCodeScanBodyModel?,
  isClosing: Boolean,
  onClose: () -> Unit,
  onClosed: () -> Unit,
) {
  val progress = remember { Animatable(0f) }
  val currentOnClosed = rememberUpdatedState(onClosed)
  val density = LocalDensity.current
  val statusBars = WindowInsets.statusBars
  val outsideTapInteractionSource = remember { MutableInteractionSource() }
  val statusBarTopPadding = remember(density) {
    with(density) { statusBars.getTop(this).toDp() }
  }

  DynamicIslandScannerStatusBarHiddenEffect()

  fun closePortal() {
    if (!isClosing) {
      onClose()
    }
  }

  BackHandler(onBack = ::closePortal)

  val scannerModel = remember(model, isClosing) {
    model?.copy(
      onQrCodeScanned = { qrCodeData ->
        if (!isClosing) {
          model.onQrCodeScanned(qrCodeData)
        }
      },
      onClose = ::closePortal
    )
  }

  LaunchedEffect(isClosing) {
    if (isClosing) {
      progress.animateTo(
        targetValue = 0f,
        animationSpec = tween(
          durationMillis = DYNAMIC_ISLAND_CLOSE_DURATION_MILLIS,
          easing = dynamicIslandEaseOut
        )
      )
      currentOnClosed.value()
    } else {
      progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(
          durationMillis = DYNAMIC_ISLAND_OPEN_DURATION_MILLIS,
          easing = dynamicIslandEaseOut
        )
      )
    }
  }

  BoxWithConstraints(
    modifier = modifier.fillMaxSize()
  ) {
    content()

    val animationProgress = progress.value.coerceIn(0f, 1f)
    val portalGeometry =
      remember(maxWidth, maxHeight, statusBarTopPadding) {
        DynamicIslandPortalGeometry(
          maxWidth = maxWidth,
          maxHeight = maxHeight,
          statusBarTopPadding = statusBarTopPadding
        )
      }
    val portalFrame = portalGeometry.frameAt(animationProgress)
    val scannerAlpha = scannerAlpha(animationProgress)
    val introLabelAlpha = introLabelAlpha(animationProgress)
    val promptAlpha = if (isClosing) 0f else maxOf(introLabelAlpha, scannerAlpha)

    Box(
      modifier = Modifier
        .fillMaxSize()
        .clickable(
          indication = null,
          interactionSource = outsideTapInteractionSource,
          onClick = ::closePortal
        )
    )

    Box(
      modifier = Modifier
        .offset(x = portalFrame.horizontalOffset, y = portalGeometry.topOffset)
        .size(width = portalFrame.width, height = portalFrame.height)
        .clip(RoundedCornerShape(portalFrame.cornerRadius))
        .clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
          onClick = {}
        )
        .background(Color.Black)
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            alpha = scannerAlpha
          }
      ) {
        if (scannerModel != null && !isClosing) {
          NativeQrCodeScanner(model = scannerModel)
        }
      }
      Label(
        modifier = Modifier
          .align(Alignment.Center)
          .graphicsLayer {
            alpha = promptAlpha
          },
        text = DYNAMIC_ISLAND_SCAN_PROMPT,
        style =
          WalletTheme.labelStyle(
            type = LabelType.Body2Bold,
            textColor = WalletTheme.colors.translucentForeground,
            treatment = Unspecified
          )
      )
    }

    if (scannerModel != null && !isClosing && scannerAlpha > 0f) {
      QrCodeScanWidgets(
        modifier = Modifier.graphicsLayer {
          alpha = scannerAlpha
        },
        model = scannerModel,
        scannerToolbarAlpha = 0f
      )
    }
  }
}

@Composable
internal expect fun NativeQrCodeScanner(model: QrCodeScanBodyModel)

@Composable
fun QrCodeScanViewFinder(strokeColor: Color = Color.White) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .drawWithCache {
        val viewFinderWidth = size.width - qrCodeViewfinderMargin.toPx() * 2
        val viewFinderSize = Size(
          width = viewFinderWidth,
          height = viewFinderWidth
        )
        val viewFinderTopLeft = Offset(
          x = qrCodeViewfinderMargin.toPx(),
          y = size.height / 2 - viewFinderWidth / 2
        )
        val viewFinderCornerRadius = CornerRadius(qrCodeViewfinderBorderRadius.toPx())
        val viewFinderBorderStroke = Stroke(width = 3.dp.toPx())
        val scrimColor = Color.Black.copy(alpha = 0.6f)

        onDrawBehind {
          with(drawContext.canvas.nativeCanvas) {
            val checkPoint = saveLayer(null, null)
            drawRect(scrimColor)
            drawRoundRect(
              color = Color.Transparent,
              blendMode = BlendMode.Clear,
              cornerRadius = viewFinderCornerRadius,
              size = viewFinderSize,
              topLeft = viewFinderTopLeft
            )
            restoreToCount(checkPoint)
          }

          drawRoundRect(
            color = strokeColor,
            style = viewFinderBorderStroke,
            cornerRadius = viewFinderCornerRadius,
            size = viewFinderSize,
            topLeft = viewFinderTopLeft
          )
        }
      }
  )
}

@Composable
fun QrCodeScanWidgets(
  modifier: Modifier = Modifier,
  model: QrCodeScanBodyModel,
  scannerToolbarAlpha: Float = 1f,
  applySystemBarsPadding: Boolean = true,
) {
  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .let { baseModifier ->
          if (applySystemBarsPadding) {
            baseModifier.systemBarsPadding()
          } else {
            baseModifier
          }
        }
        .padding(20.dp)
  ) {
    if (scannerToolbarAlpha > 0f) {
      Toolbar(
        modifier =
          Modifier
            .alpha(scannerToolbarAlpha)
            .background(color = Color.Transparent)
            .align(Alignment.TopCenter),
        designSystemChromeBackgroundColor = Color.Transparent,
        showDesignSystemBottomGradient = false,
        leadingContent = {
          ToolbarAccessory(
            model =
              ToolbarAccessoryModel.IconAccessory(
                model =
                  IconButtonModel(
                    iconModel =
                      IconModel(
                        icon = Icon.X,
                        iconSize = IconSize.Accessory,
                        iconTint = IconTint.OnTranslucent,
                        iconBackgroundType =
                          IconBackgroundType.Circle(
                            circleSize = IconSize.Regular,
                            color = IconBackgroundType.Circle.CircleColor.TranslucentBlack
                          )
                      ),
                    onClick = StandardClick(model.onClose),
                    testTag = "toolbar-close"
                  )
              )
          )
        },
        middleContent =
          model.headline?.let { headline ->
            {
              Label(
                text = headline,
                style =
                  WalletTheme.labelStyle(
                    type = LabelType.Title2,
                    textColor = WalletTheme.colors.translucentForeground,
                    treatment = Unspecified
                  )
              )
            }
          }
      )
    }
    model.reticleLabel?.let { caption ->
      Label(
        // adjust label to lower text below view finder
        modifier =
          Modifier
            .align(Alignment.Center)
            .padding(top = maxWidth),
        text = caption,
        style =
          WalletTheme.labelStyle(
            type = LabelType.Body2Bold,
            textColor = WalletTheme.colors.translucentForeground,
            treatment = Unspecified
          )
      )
    }

    if (model.showActionButtons) {
      Column(
        modifier =
          Modifier
            .align(Alignment.BottomCenter)
      ) {
        OrderedButtonPair(
          primary = model.primaryButton,
          secondary = model.secondaryButton,
          spacing = 16.dp,
          renderButton = {
            build.wallet.ui.components.button.Button(
              qrCodeActionButtonModel(buttonModel = it)
            )
          }
        )
      }
    }
  }
}

private fun lerp(
  start: Dp,
  stop: Dp,
  fraction: Float,
): Dp {
  return start + (stop - start) * fraction
}

private data class DynamicIslandPortalGeometry(
  val maxWidth: Dp,
  val maxHeight: Dp,
  val statusBarTopPadding: Dp,
) {
  val topOffset =
    dynamicIslandFallbackTopMargin +
      (statusBarTopPadding - dynamicIslandReferenceSafeAreaTop).coerceAtLeast(0.dp)
  private val maxPortalHeight =
    (maxHeight - topOffset - dynamicIslandPortalEdgeMargin)
      .coerceAtLeast(dynamicIslandIntroHeight)
  private val expandedPortalSize =
    (maxWidth - dynamicIslandPortalEdgeMargin * 2)
      .coerceAtMost(maxPortalHeight)
  private val startOffset = (maxWidth - dynamicIslandIntroWidth) / 2

  fun frameAt(progress: Float): DynamicIslandPortalFrame {
    // The portal grows from the physical Dynamic Island pill into a square scanner surface.
    // Values derived from screen size and safe area are cached in this geometry object; only the
    // frame interpolation below changes during animation frames.
    return DynamicIslandPortalFrame(
      width = lerp(dynamicIslandIntroWidth, expandedPortalSize, progress),
      height = lerp(dynamicIslandIntroHeight, expandedPortalSize, progress),
      horizontalOffset = lerp(startOffset, dynamicIslandPortalEdgeMargin, progress),
      cornerRadius = lerp(
        dynamicIslandIntroHeight / 2,
        dynamicIslandPortalBorderRadius,
        progress
      )
    )
  }
}

private data class DynamicIslandPortalFrame(
  val width: Dp,
  val height: Dp,
  val horizontalOffset: Dp,
  val cornerRadius: Dp,
)

private fun scannerAlpha(progress: Float): Float {
  return ((progress - DYNAMIC_ISLAND_SCANNER_FADE_START_PROGRESS) /
    (1f - DYNAMIC_ISLAND_SCANNER_FADE_START_PROGRESS)).coerceIn(0f, 1f)
}

private fun introLabelAlpha(progress: Float): Float {
  return (1f - (progress / DYNAMIC_ISLAND_INTRO_LABEL_FADE_END_PROGRESS))
    .coerceIn(0f, 1f)
}

private fun qrCodeActionButtonModel(buttonModel: ButtonModel): ButtonModel {
  return if (buttonModel.treatment == ButtonModel.Treatment.Translucent) {
    buttonModel.copy(treatment = ButtonModel.Treatment.Secondary)
  } else {
    buttonModel
  }
}
