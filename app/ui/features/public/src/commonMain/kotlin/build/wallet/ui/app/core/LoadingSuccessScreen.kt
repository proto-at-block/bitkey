package build.wallet.ui.app.core

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.loading.DesignSystemDotIndicator
import build.wallet.ui.components.loading.rememberShuffledDotLoadingIcon
import build.wallet.ui.tokens.LabelType

@Composable
fun LoadingSuccessScreen(
  modifier: Modifier = Modifier,
  model: LoadingSuccessBodyModel,
) {
  FormScreen(
    modifier = modifier,
    onBack = null,
    headerToMainContentSpacing = 0,
    headerContent = null,
    mainContent = {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        LoadingSuccessContent(model = model)
      }
    },
    footerContent = {
      val buttons = listOfNotNull(
        model.secondaryButton,
        model.primaryButton
      )
      if (buttons.isNotEmpty()) {
        Column {
          buttons.forEach { buttonModel ->
            if (buttonModel != buttons.first()) {
              Spacer(modifier = Modifier.height(16.dp))
            }
            Button(model = buttonModel)
          }
        }
      }
    }
  )
}

@Composable
private fun LoadingSuccessContent(
  model: LoadingSuccessBodyModel,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    LoadingSuccessAsset(state = model.state)

    Spacer(modifier = Modifier.height(20.dp))

    // Always show the label regardless of if there's a message or not so that
    // the loading and success states line up
    Label(
      text = model.message ?: " ",
      type = LabelType.Body3Mono,
      alignment = TextAlign.Center
    )

    model.description?.let { description ->
      Spacer(modifier = Modifier.height(16.dp))
      Label(
        text = description,
        type = LabelType.Body2Regular,
        alignment = TextAlign.Center
      )
    }
  }
}

@Composable
private fun LoadingSuccessAsset(
  state: LoadingSuccessBodyModel.State,
) {
  DesignSystemDotAsset(state = state)
}

@Composable
private fun DesignSystemDotAsset(state: LoadingSuccessBodyModel.State) {
  val loadingIcon = rememberShuffledDotLoadingIcon(enabled = state !is Success)
  val icon = if (state is Success) Icon.DotVerification else loadingIcon

  DesignSystemDotIndicator(
    modifier = Modifier.size(80.dp),
    icon = icon
  )
}
