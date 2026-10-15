package build.wallet.ui.app.qrcode

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
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
private val dynamicIslandPortalReticleLabelSpacing = 24.dp
private val qrCodeScanScrimColor = Color.Black.copy(alpha = 0.6f)
private val dynamicIslandPortalDarkThemeBorderColor = Color.White.copy(alpha = 0.2f)
private val dynamicIslandPortalBorderWidth = 1.dp
private val dynamicIslandPortalRingOutset = 6.dp
// Ease in and out, so the portal starts slowly from the Dynamic Island and settles slowly into it.
private val dynamicIslandEaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private const val DYNAMIC_ISLAND_OPEN_DURATION_MILLIS = 460
private const val DYNAMIC_ISLAND_CLOSE_DURATION_MILLIS = 340
private const val DYNAMIC_ISLAND_SCANNER_FADE_START_PROGRESS = 0.12f
private const val DYNAMIC_ISLAND_INTRO_LABEL_FADE_END_PROGRESS = 0.42f
private const val DYNAMIC_ISLAND_RING_FADE_DURATION_MILLIS = 200
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
  model: QrCodeScanBodyModel?,
  isClosing: Boolean,
  onClose: () -> Unit,
  onClosed: () -> Unit,
  content: @Composable () -> Unit,
) {
  val progress = remember { Animatable(0f) }
  val ringAlpha = remember { Animatable(0f) }
  val currentOnClosed = rememberUpdatedState(onClosed)
  val currentIsClosing = rememberUpdatedState(isClosing)
  val density = LocalDensity.current
  val statusBars = WindowInsets.statusBars
  val outsideTapInteractionSource = remember { MutableInteractionSource() }
  val isDarkTheme = LocalTheme.current == Theme.DARK
  val currentIsDarkTheme = rememberUpdatedState(isDarkTheme)
  val statusBarTopPadding = remember(density) {
    with(density) { statusBars.getTop(this).toDp() }
  }
  val navigationBars = WindowInsets.navigationBars
  val navigationBarBottomPadding = remember(density) {
    with(density) { navigationBars.getBottom(this).toDp() }
  }

  // Bring the status bar back as soon as the portal starts closing so its inset is restored by the
  // time the recipient screen takes over; restoring it on dispose makes that screen jump up and
  // then slide back down as the status bar reappears.
  DynamicIslandScannerStatusBarHiddenEffect(hidden = !isClosing)

  fun closePortal() {
    if (!isClosing) {
      onClose()
    }
  }

  BackHandler(onBack = ::closePortal)

  val scannerModel = remember(model, isClosing) {
    model?.copy(
      // The native scanner keeps the callback it was created with and stays up while the portal
      // closes, so check the current closing state rather than the one captured here.
      onQrCodeScanned = { qrCodeData ->
        if (!currentIsClosing.value) {
          model.onQrCodeScanned(qrCodeData)
        }
      },
      onClose = ::closePortal
    )
  }

  // The scanner model is cleared as soon as the portal starts closing. Keep the last one so the
  // caption and action buttons can fade out with the portal instead of disappearing immediately.
  val lastScannerModel = remember { mutableStateOf<QrCodeScanBodyModel?>(null) }
  SideEffect {
    if (scannerModel != null) {
      lastScannerModel.value = scannerModel
    }
  }
  val widgetsModel = scannerModel ?: lastScannerModel.value

  LaunchedEffect(isClosing) {
    if (isClosing) {
      closeDynamicIslandPortal(
        progress = progress,
        ringAlpha = ringAlpha,
        fadesRing = currentIsDarkTheme.value
      )
      currentOnClosed.value()
    } else {
      openDynamicIslandPortal(
        progress = progress,
        ringAlpha = ringAlpha,
        fadesRing = currentIsDarkTheme.value
      )
    }
  }

  BoxWithConstraints(
    modifier = modifier.fillMaxSize()
  ) {
    val animationProgress = progress.value.coerceIn(0f, 1f)

    // Fade the recipient screen out as the portal opens and back in as it closes, so its toolbar
    // and footer button don't show through under the scanner's own caption and action buttons.
    // Fading only changes what's drawn, so also hide it from accessibility services; otherwise
    // VoiceOver and Switch Control can still reach its invisible controls behind the scanner.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clearAndSetSemantics {}
        .graphicsLayer {
          alpha = 1f - animationProgress
        }
    ) {
      content()
    }

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
    val promptAlpha = if (isClosing) scannerAlpha else maxOf(introLabelAlpha, scannerAlpha)

    Box(
      modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
          alpha = animationProgress
        }
        .background(qrCodeScanScrimColor)
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
        // Keep the camera feed up while the portal closes so it fades out as it shrinks back into
        // the Dynamic Island, mirroring the open. Dropping it leaves a solid black portal, which
        // disappears against the black scrim and dark theme background.
        val cameraModel = portalCameraModel(
          isClosing = isClosing,
          scannerAlpha = scannerAlpha,
          scannerModel = scannerModel,
          retainedModel = widgetsModel
        )
        if (cameraModel != null) {
          // The camera access alert is a separate window that the portal's fade, clip and tap
          // blocker don't reach, so drop it as soon as the portal starts closing.
          NativeQrCodeScanner(model = cameraModel, showPermissionPrompts = !isClosing)
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

    if (isDarkTheme) {
      DynamicIslandPortalRing(
        portalGeometry = portalGeometry,
        portalFrame = portalFrame,
        progress = animationProgress,
        alpha = ringAlpha.value
      )
    }

    if (widgetsModel != null && scannerAlpha > 0f) {
      // The shared widgets position the reticle label for the full-screen viewfinder, which would
      // land in the middle of the recipient form. Anchor it below the portal instead.
      widgetsModel.reticleLabel?.let { reticleLabel ->
        Label(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(
              y = portalGeometry.topOffset + portalFrame.height +
                dynamicIslandPortalReticleLabelSpacing
            )
            .graphicsLayer {
              alpha = scannerAlpha
            },
          text = reticleLabel,
          style =
            WalletTheme.labelStyle(
              type = LabelType.Body2Bold,
              textColor = WalletTheme.colors.translucentForeground,
              treatment = Unspecified
            )
        )
      }
      QrCodeScanWidgets(
        // The screen container consumes the system bar insets, so pad with the raw insets instead.
        modifier = Modifier
          .padding(top = statusBarTopPadding, bottom = navigationBarBottomPadding)
          .graphicsLayer {
            alpha = scannerAlpha
          }
          // The tap blocker below only stops pointer input, so also hide the fading actions from
          // accessibility services while the portal closes.
          .then(if (isClosing) Modifier.clearAndSetSemantics {} else Modifier),
        model = widgetsModel.copy(reticleLabel = null),
        scannerToolbarAlpha = 0f,
        applySystemBarsPadding = false
      )
    }

    if (isClosing) {
      // Swallow taps on the fading action buttons while the portal closes.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = {}
          )
      )
    }
  }
}

/**
 * Grows the portal out of the Dynamic Island. In the dark theme, the ring around the island fades in
 * first, and the portal only starts to grow once the ring is fully visible.
 */
private suspend fun openDynamicIslandPortal(
  progress: Animatable<Float, AnimationVector1D>,
  ringAlpha: Animatable<Float, AnimationVector1D>,
  fadesRing: Boolean,
) {
  if (fadesRing) {
    ringAlpha.animateTo(
      targetValue = 1f,
      animationSpec = tween(
        durationMillis = DYNAMIC_ISLAND_RING_FADE_DURATION_MILLIS,
        easing = LinearEasing
      )
    )
  } else {
    ringAlpha.snapTo(1f)
  }
  progress.animateTo(
    targetValue = 1f,
    animationSpec = tween(
      durationMillis = DYNAMIC_ISLAND_OPEN_DURATION_MILLIS,
      easing = dynamicIslandEaseInOut
    )
  )
}

/**
 * Shrinks the portal back into the Dynamic Island. In the dark theme, the ring then fades out around
 * the island, so it never fades while it's moving.
 */
private suspend fun closeDynamicIslandPortal(
  progress: Animatable<Float, AnimationVector1D>,
  ringAlpha: Animatable<Float, AnimationVector1D>,
  fadesRing: Boolean,
) {
  progress.animateTo(
    targetValue = 0f,
    animationSpec = tween(
      durationMillis = DYNAMIC_ISLAND_CLOSE_DURATION_MILLIS,
      easing = dynamicIslandEaseInOut
    )
  )
  if (fadesRing) {
    ringAlpha.animateTo(
      targetValue = 0f,
      animationSpec = tween(
        durationMillis = DYNAMIC_ISLAND_RING_FADE_DURATION_MILLIS,
        easing = LinearEasing
      )
    )
  } else {
    ringAlpha.snapTo(0f)
  }
}

/**
 * Outlines the Dynamic Island portal in the dark theme, where the portal, the Dynamic Island and the
 * background are all black and the portal's animation would otherwise have no visible edge. The
 * outline starts as a ring just outside the island, which covers the portal at that size, and
 * closes in on the portal's edge as it expands.
 */
@Composable
private fun DynamicIslandPortalRing(
  portalGeometry: DynamicIslandPortalGeometry,
  portalFrame: DynamicIslandPortalFrame,
  progress: Float,
  alpha: Float,
) {
  val ringOutset = lerp(dynamicIslandPortalRingOutset, 0.dp, progress)
  Box(
    modifier = Modifier
      .offset(
        x = portalFrame.horizontalOffset - ringOutset,
        y = portalGeometry.topOffset - ringOutset
      )
      .size(
        width = portalFrame.width + ringOutset * 2,
        height = portalFrame.height + ringOutset * 2
      )
      .border(
        width = dynamicIslandPortalBorderWidth,
        color = dynamicIslandPortalDarkThemeBorderColor.copy(
          alpha = dynamicIslandPortalDarkThemeBorderColor.alpha * alpha
        ),
        shape = RoundedCornerShape(portalFrame.cornerRadius + ringOutset)
      )
  )
}

/**
 * @param showPermissionPrompts whether to show platform prompts, such as the camera access alert,
 * alongside the camera preview. Turn these off when the scanner only stays up to fade out.
 */
@Composable
internal expect fun NativeQrCodeScanner(
  model: QrCodeScanBodyModel,
  showPermissionPrompts: Boolean = true,
)

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
        onDrawBehind {
          with(drawContext.canvas.nativeCanvas) {
            val checkPoint = saveLayer(null, null)
            drawRect(qrCodeScanScrimColor)
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

/**
 * The model to run the camera with: the live scanner model while the portal is open, then the last
 * one while the portal closes, until the camera has faded out.
 */
private fun portalCameraModel(
  isClosing: Boolean,
  scannerAlpha: Float,
  scannerModel: QrCodeScanBodyModel?,
  retainedModel: QrCodeScanBodyModel?,
): QrCodeScanBodyModel? {
  return when {
    !isClosing -> scannerModel
    scannerAlpha > 0f -> retainedModel
    else -> null
  }
}

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
