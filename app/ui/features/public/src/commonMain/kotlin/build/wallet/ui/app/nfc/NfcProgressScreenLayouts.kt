package build.wallet.ui.app.nfc

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bitkey.account.HardwareType
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.ios_nfc_background
import bitkey.ui.framework_public.generated.resources.ios_nfc_background_w1
import bitkey.ui.framework_public.generated.resources.ios_nfc_tap
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.components.video.VideoScalingMode
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.model.button.ButtonModel.Treatment.Translucent
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.LocalIsPreviewTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

internal const val ANDROID_NFC_PHONE_ASPECT_RATIO = 490f / 778f
internal const val ANDROID_NFC_PHONE_SCALE_FACTOR = 1.5f
internal const val ANDROID_NFC_PHONE_WIDTH_FRACTION = 0.46f * ANDROID_NFC_PHONE_SCALE_FACTOR
internal val ANDROID_NFC_PHONE_MAX_WIDTH = 160.dp * ANDROID_NFC_PHONE_SCALE_FACTOR
internal val ANDROID_NFC_PHONE_BOTTOM_SPACING = 40.dp
internal const val ANDROID_NFC_V2_STATUS_BLOCK_TOP_SPACER_WEIGHT = 1f
internal const val ANDROID_NFC_V2_STATUS_TO_PHONE_SPACER_WEIGHT = 1f
internal val ANDROID_NFC_V2_STATUS_BLOCK_OFFSET = 80.dp
internal val ANDROID_NFC_TOOLBAR_HORIZONTAL_PADDING = 20.dp
private val IOS_NFC_STATUS_ICON_TOP_PADDING = 32.dp
private const val IOS_VIDEO_PLACEHOLDER_REVEAL_DELAY_MILLIS = 350
private const val IOS_VIDEO_PLACEHOLDER_FADE_DURATION_MILLIS = 150

/**
 * Generic iOS layout for NFC progress screens with gradient background.
 *
 * @param modifier Modifier for the root container
 * @param hardwareType The hardware type being tapped (W1 vs W3), used for selecting device imagery
 * @param statusContent Content to display below the NFC icon (typically status labels)
 */
@Composable
fun NfcProgressScreenIosLayout(
  modifier: Modifier = Modifier,
  hardwareType: HardwareType = HardwareType.W3,
  backgroundColor: Color = Color.Black,
  backgroundPainter: Painter? = null,
  backgroundVideoResourcePath: String? = null,
  backgroundVideoIsLooping: Boolean = true,
  backgroundVideoTopPadding: Dp = 0.dp,
  backgroundTopPadding: Dp = 200.dp,
  statusTopPadding: Dp = 112.dp,
  showDefaultHardwareBackground: Boolean = true,
  statusContent: @Composable ColumnScope.() -> Unit,
) {
  val designSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val nfcBlue = WalletTheme.colors.nfcBlue.copy(alpha = 0.6f)
  val showDesignSystemBackground =
    designSystemV2Enabled || backgroundPainter != null || backgroundVideoResourcePath != null

  NfcIosBackgroundLayout(
    modifier = modifier,
    hardwareType = hardwareType,
    backgroundColor = backgroundColor,
    backgroundPainter = backgroundPainter,
    backgroundVideoResourcePath = backgroundVideoResourcePath,
    backgroundVideoIsLooping = backgroundVideoIsLooping,
    backgroundVideoTopPadding = backgroundVideoTopPadding,
    backgroundTopPadding = backgroundTopPadding,
    showDefaultHardwareBackground = showDefaultHardwareBackground
  ) {
    if (showDesignSystemBackground) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = statusTopPadding, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Image(
          modifier =
            if (designSystemV2Enabled) Modifier.padding(top = IOS_NFC_STATUS_ICON_TOP_PADDING) else Modifier,
          alignment = Alignment.Center,
          painter = painterResource(Res.drawable.ios_nfc_tap),
          contentDescription = null,
          colorFilter =
            if (designSystemV2Enabled) ColorFilter.tint(WalletTheme.colors.foreground) else null
        )
        statusContent()
      }
    } else {
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(48.dp))
        Box {
          Spacer(
            modifier = Modifier
              .matchParentSize()
              .drawBehind {
                drawRect(
                  brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, nfcBlue, Color.Transparent),
                    startY = size.height,
                    endY = 0f
                  )
                )
              }
              .blur(28.dp, BlurredEdgeTreatment.Rectangle)
          )
          Column(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Image(
              modifier =
                if (designSystemV2Enabled) Modifier.padding(top = IOS_NFC_STATUS_ICON_TOP_PADDING) else Modifier,
              alignment = Alignment.Center,
              painter = painterResource(Res.drawable.ios_nfc_tap),
              contentDescription = null,
              colorFilter =
                if (designSystemV2Enabled) ColorFilter.tint(WalletTheme.colors.foreground) else null
            )
            statusContent()
          }
        }
      }
    }
  }
}

@Composable
internal fun NfcIosBackgroundLayout(
  modifier: Modifier = Modifier,
  hardwareType: HardwareType = HardwareType.W3,
  backgroundColor: Color = Color.Black,
  backgroundPainter: Painter? = null,
  backgroundVideoResourcePath: String? = null,
  backgroundVideoIsLooping: Boolean = true,
  backgroundVideoTopPadding: Dp = 0.dp,
  backgroundTopPadding: Dp = 200.dp,
  showDefaultHardwareBackground: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val isPreviewTheme = LocalIsPreviewTheme.current
  val showDesignSystemBackground =
    LocalDesignSystemUpdatesEnabled.current || backgroundPainter != null || backgroundVideoResourcePath != null
  val effectiveBackgroundVideoResourcePath =
    if (isPreviewTheme) {
      null
    } else {
      backgroundVideoResourcePath
    }
  val backgroundModifier =
    if (showDesignSystemBackground) {
      modifier
        .fillMaxSize()
        .background(backgroundColor)
    } else {
      modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(Color.Black, WalletTheme.colors.nfcBlue)
          )
        )
    }

  if (showDesignSystemBackground) {
    Box(modifier = backgroundModifier) {
      when {
        effectiveBackgroundVideoResourcePath != null -> {
          val hasBackgroundPlaceholder = backgroundPainter != null
          var showPlaceholder by remember(hasBackgroundPlaceholder, effectiveBackgroundVideoResourcePath) {
            mutableStateOf(hasBackgroundPlaceholder)
          }
          LaunchedEffect(hasBackgroundPlaceholder, effectiveBackgroundVideoResourcePath) {
            showPlaceholder = hasBackgroundPlaceholder
            if (hasBackgroundPlaceholder) {
              delay(IOS_VIDEO_PLACEHOLDER_REVEAL_DELAY_MILLIS.toLong())
              showPlaceholder = false
            }
          }
          val placeholderAlpha by animateFloatAsState(
            targetValue = if (showPlaceholder) 1f else 0f,
            animationSpec = tween(durationMillis = IOS_VIDEO_PLACEHOLDER_FADE_DURATION_MILLIS),
            label = "iosVideoPlaceholderAlpha"
          )
          VideoPlayer(
            modifier =
              Modifier
                .fillMaxSize()
                .padding(top = backgroundVideoTopPadding),
            resourcePath = effectiveBackgroundVideoResourcePath,
            isLooping = backgroundVideoIsLooping,
            backgroundColor = backgroundColor,
            scalingMode = VideoScalingMode.CROP
          )
          backgroundPainter?.let { painter ->
            Image(
              painter = painter,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier =
                Modifier
                  .fillMaxSize()
                  .padding(top = backgroundVideoTopPadding)
                  .alpha(placeholderAlpha)
            )
          }
        }

        backgroundPainter != null -> {
          Image(
            painter = backgroundPainter,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier =
              Modifier
                .padding(top = backgroundTopPadding)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
          )
        }

        showDefaultHardwareBackground -> {
          val backgroundDrawable = when (hardwareType) {
            HardwareType.W1 -> Res.drawable.ios_nfc_background_w1
            HardwareType.W3 -> Res.drawable.ios_nfc_background
          }
          Image(
            painter = painterResource(backgroundDrawable),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier =
              Modifier
                .padding(top = backgroundTopPadding)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
          )
        }

        else -> Unit
      }
      content()
    }
  } else {
    Box(modifier = backgroundModifier, content = content)
  }
}

@Composable
internal fun NfcProgressScreenAndroidLayoutV2(
  modifier: Modifier = Modifier,
  onCancel: (() -> Unit)?,
  enableBackGesture: Boolean = true,
  topContent: @Composable BoxScope.() -> Unit = {},
  statusContent: @Composable ColumnScope.() -> Unit,
  bottomContent: @Composable () -> Unit = {
    Spacer(
      modifier =
        Modifier
          .fillMaxWidth(ANDROID_NFC_PHONE_WIDTH_FRACTION)
          .widthIn(max = ANDROID_NFC_PHONE_MAX_WIDTH)
          .aspectRatio(ANDROID_NFC_PHONE_ASPECT_RATIO)
    )
  },
) {
  // Always intercept back gesture; only invoke onCancel when enabled
  BackHandler {
    if (enableBackGesture) {
      onCancel?.invoke()
    }
  }

  val cancelButtonAlpha: Float by animateFloatAsState(
    targetValue = if (onCancel == null) 0f else 1f,
    label = "nfcV2CancelButtonAlphaAnimation"
  )

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(WalletTheme.colors.background)
  ) {
    topContent()

    Column(
      modifier =
        Modifier
          .padding(horizontal = ANDROID_NFC_TOOLBAR_HORIZONTAL_PADDING)
          .navigationBarsPadding()
          .fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.weight(ANDROID_NFC_V2_STATUS_BLOCK_TOP_SPACER_WEIGHT))

      Column(
        modifier = Modifier.offset(y = ANDROID_NFC_V2_STATUS_BLOCK_OFFSET),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = statusContent
      )

      Spacer(Modifier.weight(ANDROID_NFC_V2_STATUS_TO_PHONE_SPACER_WEIGHT))

      bottomContent()

      Spacer(modifier = Modifier.height(ANDROID_NFC_PHONE_BOTTOM_SPACING))

      Button(
        text = "Cancel",
        modifier = Modifier.alpha(cancelButtonAlpha),
        treatment = Secondary,
        size = Footer,
        onClick = StandardClick {
          onCancel?.invoke()
        }
      )

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

/**
 * Generic Android layout for NFC progress screens with blur background and status indicator.
 *
 * @param modifier Modifier for the root container
 * @param onCancel Optional callback for cancel button (null hides button)
 * @param enableBackGesture Whether the back gesture triggers onCancel (default true)
 * @param statusIndicator Status indicator composable (typically NfcProgressStatusIndicator)
 * @param statusLabel Status label composable (typically NfcStatusLabel)
 */
@Composable
fun NfcProgressScreenAndroidLayout(
  modifier: Modifier = Modifier,
  onCancel: (() -> Unit)?,
  enableBackGesture: Boolean = true,
  statusIndicator: @Composable () -> Unit,
  statusLabel: @Composable () -> Unit,
) {
  // Always intercept back gesture; only invoke onCancel when enabled
  BackHandler {
    if (enableBackGesture) {
      onCancel?.invoke()
    }
  }

  NfcBlurBackground {
    Column(
      modifier =
        modifier
          .background(WalletTheme.colors.foreground.copy(alpha = 0.1F))
          .padding(horizontal = 20.dp)
          .navigationBarsPadding()
          .fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.weight(1F))

      statusIndicator()

      Box(modifier = Modifier.weight(1F)) {
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
          statusLabel()
        }
      }

      Spacer(Modifier.weight(1F))

      Button(
        text = "Cancel",
        modifier =
          Modifier.alpha(
            when (onCancel) {
              null -> 0f
              else -> 1f
            }
          ),
        treatment = Translucent,
        size = Footer,
        onClick = StandardClick {
          onCancel?.invoke()
        }
      )
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

