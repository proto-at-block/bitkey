package build.wallet.ui.app.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.bitkey_full_logo
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.compose.resId
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.painterResource

@Composable
fun ChooseAccountAccessScreen(
  modifier: Modifier = Modifier,
  model: ChooseAccountAccessModel,
) {
  Box(
    modifier =
      modifier
        .fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    BoxWithConstraints {
      VideoPlayer(
        modifier = Modifier
          .wrapContentSize(Alignment.Center, unbounded = true)
          .size(maxHeight),
        resourcePath = Res.getVideoResource("welcome"),
        isLooping = true
      )
    }

    Column(
      modifier =
        Modifier
          .fillMaxSize(),
      verticalArrangement = Arrangement.Bottom
    ) {
      Column(
        modifier =
          Modifier
            .background(
              brush = Brush.linearGradient(
                colors = listOf(
                  Color.Transparent,
                  Color.Black.copy(alpha = 0.8F)
                ),
                start = Offset.Zero,
                end = Offset(0f, Float.POSITIVE_INFINITY)
              )
            )
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
      ) {
        Label(
          text = model.title,
          type = LabelType.Display3,
          treatment = LabelTreatment.Unspecified,
          color = Color.White
        )
        Label(
          text = model.subtitle,
          type = LabelType.Body2Regular,
          treatment = LabelTreatment.Unspecified,
          color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        model.buttons.forEachIndexed { index, buttonModel ->
          Button(model = buttonModel)
          if (index < model.buttons.lastIndex) {
            Spacer(modifier = Modifier.height(16.dp))
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    }

    Box(
      modifier =
        Modifier
          .align(Companion.TopStart)
          .resId("logo")
          .padding(horizontal = 20.dp, vertical = 52.dp)
          .height(25.dp)
          .clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
          ) { model.onLogoClick() }
    ) {
      Icon(
        painter = painterResource(Res.drawable.bitkey_full_logo),
        contentDescription = "Bitkey Logo",
        tint = Color.White
      )
    }
  }
}
