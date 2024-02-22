package build.wallet.ui.app.core

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.android.ui.core.R
import build.wallet.statemachine.core.Icon.LargeIconCheckFilled
import build.wallet.statemachine.core.SuccessBodyModel
import build.wallet.statemachine.core.SuccessBodyModel.Style.Explicit
import build.wallet.statemachine.core.SuccessBodyModel.Style.Implicit
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.header.Header
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun SuccessScreen(model: SuccessBodyModel) {
  when (val style = model.style) {
    is Explicit ->
      FormScreen(
        onBack = style.primaryButton.onClick,
        headerContent = {
          Header(
            model = FormHeaderModel(
              headline = model.title,
              subline = model.message,
              icon = LargeIconCheckFilled
            )
          )
        },
        footerContent = {
          Button(
            text = style.primaryButton.text,
            treatment = Primary,
            size = Footer,
            onClick = Click.StandardClick { style.primaryButton.onClick() }
          )
        }
      )

    is Implicit -> ImplicitSuccessScreen(model)
  }
}

@Composable
fun ImplicitSuccessScreen(model: SuccessBodyModel) {
  val circleColor = WalletTheme.colors.primary
  val successAnimationComposition by rememberLottieComposition(
    LottieCompositionSpec.RawRes(R.raw.success)
  )

  Column(
    modifier =
      Modifier
        .fillMaxSize(),
    verticalArrangement = Arrangement.Center
  ) {
    Header(
      iconContent = {
        Box(
          contentAlignment = Alignment.Center
        ) {
          Canvas(
            modifier = Modifier.size(64.dp),
            onDraw = {
              drawCircle(circleColor)
            }
          )
          LottieAnimation(
            composition = successAnimationComposition,
            iterations = 1
          )
        }
      },
      headline = model.title,
      subline = model.message?.let { AnnotatedString(it) },
      horizontalAlignment = Alignment.CenterHorizontally
    )
  }
}

@Preview
@Composable
internal fun SuccessWithMessagePreview() {
  PreviewWalletTheme {
    SuccessScreen(
      model =
        SuccessBodyModel(
          title = "You have succeeded",
          message = "Congratulations for doing such a great job.",
          style = Explicit(onPrimaryButtonClick = {}),
          id = null
        )
    )
  }
}

@Preview
@Composable
internal fun SuccessWithoutMessagePreview() {
  PreviewWalletTheme {
    SuccessScreen(
      model =
        SuccessBodyModel(
          title = "You have succeeded",
          style = Explicit(onPrimaryButtonClick = {}),
          id = null
        )
    )
  }
}

@Preview
@Composable
internal fun ImplicitSuccessPreview() {
  PreviewWalletTheme {
    SuccessScreen(
      model =
        SuccessBodyModel(
          title = "You have succeeded",
          style = Implicit,
          id = null
        )
    )
  }
}
