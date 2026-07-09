package build.wallet.ui.app.nfc

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import org.jetbrains.compose.resources.painterResource

internal const val ANDROID_NFC_PHONE_ASPECT_RATIO = 490f / 778f
internal const val ANDROID_NFC_PHONE_SCALE_FACTOR = 1.5f
internal const val ANDROID_NFC_PHONE_WIDTH_FRACTION = 0.46f * ANDROID_NFC_PHONE_SCALE_FACTOR
internal val ANDROID_NFC_PHONE_MAX_WIDTH = 160.dp * ANDROID_NFC_PHONE_SCALE_FACTOR
internal val ANDROID_NFC_PHONE_BOTTOM_SPACING = 40.dp
internal const val ANDROID_NFC_STATUS_BLOCK_TOP_SPACER_WEIGHT = 1f
internal const val ANDROID_NFC_STATUS_TO_PHONE_SPACER_WEIGHT = 1f
internal val ANDROID_NFC_STATUS_BLOCK_OFFSET = 80.dp
internal val ANDROID_NFC_TOOLBAR_HORIZONTAL_PADDING = 20.dp
private val IOS_NFC_STATUS_ICON_TOP_PADDING = 32.dp

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
  backgroundTopPadding: Dp = 200.dp,
  statusTopPadding: Dp = 112.dp,
  showDefaultHardwareBackground: Boolean = true,
  statusContent: @Composable ColumnScope.() -> Unit,
) {
  NfcIosBackgroundLayout(
    modifier = modifier,
    hardwareType = hardwareType,
    backgroundColor = backgroundColor,
    backgroundPainter = backgroundPainter,
    backgroundTopPadding = backgroundTopPadding,
    showDefaultHardwareBackground = showDefaultHardwareBackground
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(top = statusTopPadding, start = 16.dp, end = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Image(
        modifier = Modifier.padding(top = IOS_NFC_STATUS_ICON_TOP_PADDING),
        alignment = Alignment.Center,
        painter = painterResource(Res.drawable.ios_nfc_tap),
        contentDescription = null,
        colorFilter = ColorFilter.tint(WalletTheme.colors.foreground)
      )
      statusContent()
    }
  }
}

@Composable
internal fun NfcIosBackgroundLayout(
  modifier: Modifier = Modifier,
  hardwareType: HardwareType = HardwareType.W3,
  backgroundColor: Color = Color.Black,
  backgroundPainter: Painter? = null,
  backgroundTopPadding: Dp = 200.dp,
  showDefaultHardwareBackground: Boolean = true,
  content: @Composable BoxScope.() -> Unit,
) {
  val backgroundModifier =
    modifier
      .fillMaxSize()
      .background(backgroundColor)

  Box(modifier = backgroundModifier) {
    when {
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
}

@Composable
internal fun NfcProgressScreenAndroidLayout(
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
    label = "nfcCancelButtonAlphaAnimation"
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
      Spacer(Modifier.weight(ANDROID_NFC_STATUS_BLOCK_TOP_SPACER_WEIGHT))

      Column(
        modifier = Modifier.offset(y = ANDROID_NFC_STATUS_BLOCK_OFFSET),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = statusContent
      )

      Spacer(Modifier.weight(ANDROID_NFC_STATUS_TO_PHONE_SPACER_WEIGHT))

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
