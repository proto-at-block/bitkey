package build.wallet.ui.app.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import bitkey.ui.framework_public.generated.resources.*
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.button.buttonStyle
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.buildAnnotatedString
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.components.video.VideoScalingMode
import build.wallet.ui.compose.resId
import build.wallet.ui.model.button.ButtonModel.Size
import build.wallet.ui.model.button.ButtonModel.Treatment.*
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.painterResource

@Composable
fun ChooseAccountAccessScreen(
  modifier: Modifier = Modifier,
  model: ChooseAccountAccessModel,
) {
  val backgroundColor = Color.Black
  val contentTint = WalletTheme.colors.bitkeyGetStartedTint
  val subtitleTint = contentTint.copy(alpha = 0.72f)
  val legalNoticeText = model.legalNotice.buildAnnotatedString()

  if (model.showW3Video) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .background(backgroundColor)
    ) {
      VideoPlayer(
        modifier = Modifier.matchParentSize(),
        resourcePath = chooseAccountAccessHeroVideoResource(),
        isLooping = true,
        backgroundColor = backgroundColor,
        scalingMode = VideoScalingMode.CROP
      )

      Column(
        modifier = Modifier
          .fillMaxSize()
          .zIndex(1f)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .systemBarsPadding()
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

        Spacer(modifier = Modifier.weight(1f))

        ChooseAccountAccessFooter(
          model = model,
          legalNoticeText = legalNoticeText,
          backgroundColor = backgroundColor,
          contentTint = contentTint,
          subtitleTint = subtitleTint
        )
      }
    }
  } else {
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

      ChooseAccountAccessFooter(
        model = model,
        legalNoticeText = legalNoticeText,
        backgroundColor = backgroundColor,
        contentTint = contentTint,
        subtitleTint = subtitleTint
      )
    }
  }
}

@Composable
private fun ChooseAccountAccessFooter(
  model: ChooseAccountAccessModel,
  legalNoticeText: AnnotatedString,
  backgroundColor: Color,
  contentTint: Color,
  subtitleTint: Color,
) {
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
      color = subtitleTint
    )
  }
}
