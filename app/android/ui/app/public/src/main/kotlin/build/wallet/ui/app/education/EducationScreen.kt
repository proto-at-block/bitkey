package build.wallet.ui.app.education

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.education.EducationBodyModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.progress.LinearProgressIndicator
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun EducationScreen(model: EducationBodyModel) {
  BackHandler {
    model.onBack()
  }
  Column(modifier = Modifier.padding(20.dp)) {
    EducationToolbar(model.progressPercentage, model.onDismiss)
    AnimatedContent(
      targetState = model,
      label = "Education Screen Item",
      transitionSpec = {
        fadeIn() togetherWith fadeOut()
      }
    ) { model ->
      EducationItem(model = model)
    }
  }
}

@Composable
private fun EducationToolbar(
  progress: Float,
  onClose: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(
      iconModel =
        IconModel(
          icon = Icon.SmallIconX,
          iconSize = IconSize.Accessory,
          iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Regular)
        ),
      onClick = onClose
    )
    Spacer(Modifier.width(20.dp))
    LinearProgressIndicator(modifier = Modifier.weight(1F), progress = progress)
    Spacer(modifier = Modifier.width(52.dp))
  }
}

@Composable
private fun EducationItem(model: EducationBodyModel) {
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .clickable(
          interactionSource = MutableInteractionSource(),
          indication = null
        ) { model.onClick() }
  ) {
    Column(
      modifier = Modifier.align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Label(text = model.title, type = LabelType.Body1Medium, alignment = TextAlign.Center)
      model.subtitle?.let {
        Spacer(Modifier.height(16.dp))
        Label(
          text = it,
          type = LabelType.Body2Regular,
          alignment = TextAlign.Center,
          treatment = LabelTreatment.Secondary
        )
      }
    }

    Column(modifier = Modifier.align(Alignment.BottomCenter)) {
      model.primaryButton?.let {
        Button(model = it)
      }
      model.secondaryButton?.let {
        Spacer(Modifier.height(16.dp))
        Button(model = it)
      }
    }
  }
}

@Preview
@Composable
fun PreviewExplainerScreen() {
  PreviewWalletTheme {
    EducationScreen(
      model =
        EducationBodyModel(
          progressPercentage = 0.5f,
          onDismiss = {},
          title = "This is the title of the education screen",
          subtitle = "This is the subtitle of the education screen",
          primaryButton =
            ButtonModel(
              text = "Primary Button",
              size = ButtonModel.Size.Footer,
              onClick = StandardClick {}
            ),
          secondaryButton =
            ButtonModel(
              text = "Secondary Button",
              size = ButtonModel.Size.Footer,
              treatment = ButtonModel.Treatment.Secondary,
              onClick = StandardClick {}
            ),
          onClick = {},
          onBack = {}
        )
    )
  }
}
