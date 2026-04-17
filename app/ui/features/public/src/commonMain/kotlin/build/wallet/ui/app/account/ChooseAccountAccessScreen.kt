package build.wallet.ui.app.account

import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement.SpaceBetween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import bitkey.ui.framework_public.generated.resources.*
import build.wallet.platform.sensor.SensorData
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.ui.app.LocalAccelerometer
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.button.buttonStyle
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.compose.resId
import build.wallet.ui.model.button.ButtonModel.Size
import build.wallet.ui.model.button.ButtonModel.Treatment.*
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.LabelType.Body2Regular
import build.wallet.ui.tokens.LabelType.Display3
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
fun ChooseAccountAccessScreen(
  modifier: Modifier = Modifier,
  model: ChooseAccountAccessModel,
) {
  if (LocalDesignSystemUpdatesEnabled.current) {
    ChooseAccountAccessDesignSystemV2Screen(
      modifier = modifier,
      model = model
    )
  } else {
    ChooseAccountAccessLegacyScreen(
      modifier = modifier,
      model = model
    )
  }
}

@Composable
private fun ChooseAccountAccessDesignSystemV2Screen(
  modifier: Modifier = Modifier,
  model: ChooseAccountAccessModel,
) {
  val backgroundColor = Color.Black
  val contentTint = WalletTheme.colors.bitkeyGetStartedTint
  val subtitleTint = contentTint.copy(alpha = 0.72f)
  val legalNotice = model.legalNotice
  val legalNoticeText = remember(legalNotice) {
    buildAnnotatedString {
      append(legalNotice.string)

      legalNotice.linkedSubstrings.forEach { linkedSubstring ->
        addStyle(
          style = SpanStyle(
            textDecoration = if (legalNotice.underline) {
              TextDecoration.Underline
            } else {
              TextDecoration.None
            }
          ),
          start = linkedSubstring.range.first,
          end = linkedSubstring.range.last + 1
        )
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(backgroundColor)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
    ) {
      Image(
        modifier = Modifier
          .resId("logo")
          .align(Alignment.TopCenter)
          .padding(top = 40.dp)
          .size(48.dp)
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = model.onLogoClick
          ),
        painter = painterResource(Res.drawable.bitkey_icon_mark),
        contentDescription = "Bitkey Icon Mark",
        colorFilter = ColorFilter.tint(contentTint)
      )
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .padding(vertical = 24.dp),
      contentAlignment = Alignment.Center
    ) {
      Image(
        modifier = Modifier
          .widthIn(max = 250.dp)
          .aspectRatio(1f),
        painter = painterResource(Res.drawable.bitkey_rotate_dark_poster),
        contentDescription = null
      )
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 20.dp)
        .padding(horizontal = 20.dp)
        .navigationBarsPadding(),
      verticalArrangement = Arrangement.Bottom
    ) {
      val setUpWalletBaseStyle = WalletTheme.buttonStyle(
        treatment = Primary,
        size = Size.Footer
      )
      val setUpWalletButtonStyle = setUpWalletBaseStyle.copy(
        backgroundColor = contentTint,
        textStyle = setUpWalletBaseStyle.textStyle.copy(
          color = backgroundColor
        ),
        iconColor = backgroundColor
      )
      Button(
        text = "Set up a new wallet",
        style = setUpWalletButtonStyle,
        onClick = { model.buttons.first().onClick() }
      )
      Spacer(modifier = Modifier.height(16.dp))
      val moreOptionsBaseStyle = WalletTheme.buttonStyle(
        treatment = Secondary,
        size = Size.Footer
      )
      val moreOptionsButtonStyle = moreOptionsBaseStyle.copy(
        backgroundColor = contentTint.copy(alpha = 0.12f),
        textStyle = moreOptionsBaseStyle.textStyle.copy(color = contentTint),
        iconColor = contentTint
      )
      Button(
        text = "More options",
        style = moreOptionsButtonStyle,
        onClick = { model.buttons.last().onClick() }
      )
      Spacer(modifier = Modifier.height(16.dp))
      Label(
        text = legalNoticeText,
        type = LabelType.Body4Mono,
        alignment = TextAlign.Center,
        treatment = LabelTreatment.Unspecified,
        color = subtitleTint,
        onClick = { index ->
          legalNotice.linkedSubstrings.firstOrNull { index in it.range }?.onClick()
        }
      )
    }
  }
}

@Composable
private fun ChooseAccountAccessLegacyScreen(
  modifier: Modifier = Modifier,
  model: ChooseAccountAccessModel,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(WalletTheme.colors.surfaceMarigold),
    verticalArrangement = SpaceBetween
  ) {
    Image(
      modifier = Modifier
        .resId("logo")
        .padding(vertical = 52.dp)
        .padding(start = 20.dp)
        .height(25.dp)
        .clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
          onClick = model.onLogoClick
        ),
      painter = painterResource(Res.drawable.bitkey_full_logo),
      contentDescription = "Bitkey Logo",
      colorFilter = ColorFilter.tint(WalletTheme.colors.surfaceCorian)
    )
    BoxWithConstraints(
      modifier = Modifier
        .weight(1f),
      contentAlignment = Alignment.TopCenter
    ) {
      val accelerometer = LocalAccelerometer.current
      val accelerometerOffset by remember {
        accelerometer?.sensorEvents ?: emptyFlow()
      }.collectAsState(SensorData(0, 0))
      val drawables = remember {
        listOf(
          Res.drawable.onboard_key,
          Res.drawable.onboard_top,
          Res.drawable.onboard_middle,
          Res.drawable.onboard_bottom
        )
      }
      drawables.forEachIndexed { index, drawable ->
        val targetOffset by remember(constraints.maxHeight) {
          derivedStateOf {
            val multiplierX = 1f + ((index + 1) * 0.6f)
            val multiplierY = 1f + (index * 0.4f)
            val interval = constraints.maxHeight / 4
            val baseOffsetY = interval * 0.5f
            val itemOffsetY = (index * interval) * 0.6f
            val accelerometerOffsetY = (accelerometerOffset.y * multiplierY).roundToInt()
            val offsetY = when (index) {
              0 -> 0
              else -> (baseOffsetY + itemOffsetY)
                .roundToInt()
                .coerceAtLeast(0)
            }
            val offsetX = (accelerometerOffset.x * multiplierX).roundToInt()
            IntOffset(
              x = offsetX,
              y = offsetY + accelerometerOffsetY
            )
          }
        }
        val animatedOffset by animateIntOffsetAsState(targetOffset)
        Image(
          modifier = Modifier
            .padding(horizontal = 90.dp)
            .offset { animatedOffset }
            .zIndex(4f - index),
          painter = painterResource(drawable),
          contentDescription = null
        )
      }
    }
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 20.dp)
        .padding(horizontal = 20.dp)
        .systemBarsPadding(),
      verticalArrangement = Arrangement.Bottom
    ) {
      Label(
        text = model.title,
        type = Display3,
        treatment = LabelTreatment.Unspecified,
        color = WalletTheme.colors.surfaceCorian
      )
      Label(
        text = model.subtitle,
        type = Body2Regular,
        treatment = LabelTreatment.Unspecified,
        color = WalletTheme.colors.surfaceCorian
      )
      Spacer(modifier = Modifier.height(16.dp))
      val setUpWalletButtonStyle = WalletTheme.buttonStyle(
        treatment = Primary,
        size = Size.Footer
      )
      Button(
        text = "Set up a new wallet",
        style = setUpWalletButtonStyle.copy(
          backgroundColor = WalletTheme.colors.surfaceCorian,
          textStyle = setUpWalletButtonStyle.textStyle.copy(
            color = Color.White
          )
        ),
        onClick = { model.buttons.first().onClick() }
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(
        text = "More options",
        style = WalletTheme.buttonStyle(
          treatment = Grayscale20,
          size = Size.Footer
        ),
        onClick = { model.buttons.last().onClick() }
      )
    }
  }
}
