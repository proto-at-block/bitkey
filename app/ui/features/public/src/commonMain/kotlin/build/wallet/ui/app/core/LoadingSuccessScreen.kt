package build.wallet.ui.app.core

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Start
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.loader_static
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.loading.DesignSystemDotIndicator
import build.wallet.ui.components.loading.rememberShuffledDotLoadingIcon
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import io.github.alexzhirkevich.compottie.*
import org.jetbrains.compose.resources.vectorResource

@Composable
fun LoadingSuccessScreen(
  modifier: Modifier = Modifier,
  model: LoadingSuccessBodyModel,
) {
  val currentTheme = LocalTheme.current
  val isDesignSystemV2Enabled = true
  val painter = if (isDesignSystemV2Enabled) {
    null
  } else {
    val loadingAnimationComposition by rememberLottieComposition {
      LottieCompositionSpec.JsonString(
        when (currentTheme) {
          Theme.LIGHT ->
            Res.readBytes("files/loading_and_success.json").decodeToString()
          Theme.DARK ->
            Res.readBytes("files/loading_and_success_dark.json").decodeToString()
        }
      )
    }

    rememberLottiePainter(
      composition = loadingAnimationComposition,
      iterations = if (model.state is Success) 1 else Compottie.IterateForever,
      speed = if (model.state is Success) 1.5f else 1f,
      clipSpec = LottieClipSpec.Progress(
        min = 0f,
        max = if (model.state is Success) {
          1f
        } else {
          when (currentTheme) {
            Theme.LIGHT -> 0.3f
            Theme.DARK -> 0.5f
          }
        }
      )
    )
  }

  FormScreen(
    modifier = modifier,
    onBack = null,
    headerToMainContentSpacing = if (isDesignSystemV2Enabled) 0 else 16,
    headerContent = if (isDesignSystemV2Enabled) {
      null
    } else {
      {
        LoadingSuccessContent(
          model = model,
          painter = painter,
          isCentered = false,
          isDesignSystemV2Enabled = false
        )
      }
    },
    mainContent = if (isDesignSystemV2Enabled) {
      {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
          contentAlignment = Alignment.Center
        ) {
          LoadingSuccessContent(
            model = model,
            painter = painter,
            isCentered = true,
            isDesignSystemV2Enabled = true
          )
        }
      }
    } else {
      {}
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
  painter: Painter?,
  isCentered: Boolean,
  isDesignSystemV2Enabled: Boolean,
) {
  val useDesignSystemV2TitleTypography = isDesignSystemV2Enabled
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isCentered) Alignment.CenterHorizontally else Start
  ) {
    LoadingSuccessAsset(
      painter = painter,
      isDesignSystemV2Enabled = isDesignSystemV2Enabled,
      state = model.state
    )

    Spacer(modifier = Modifier.height(if (useDesignSystemV2TitleTypography) 20.dp else 17.dp))

    // Always show the label regardless of if there's a message or not so that
    // the loading and success states line up
    Label(
      text = model.message ?: " ",
      type = if (useDesignSystemV2TitleTypography) LabelType.Body3Mono else LabelType.Title1,
      alignment = if (isCentered) TextAlign.Center else TextAlign.Start
    )

    model.description?.let { description ->
      Spacer(modifier = Modifier.height(16.dp))
      Label(
        text = description,
        type = LabelType.Body2Regular,
        alignment = if (isCentered) TextAlign.Center else TextAlign.Start
      )
    }
  }
}

@Composable
private fun LoadingSuccessAsset(
  painter: Painter?,
  isDesignSystemV2Enabled: Boolean,
  state: LoadingSuccessBodyModel.State,
) {
  when {
    isDesignSystemV2Enabled -> DesignSystemDotAsset(state = state)
    LocalIsPreviewTheme.current -> {
      Image(
        imageVector = vectorResource(Res.drawable.loader_static),
        contentDescription = null,
        modifier = Modifier.size(64.dp)
      )
    }
    painter != null -> {
      Image(
        painter = painter,
        modifier = Modifier.size(64.dp),
        contentScale = ContentScale.FillBounds,
        contentDescription = null
      )
    }
  }
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
