package build.wallet.ui.app.core

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.Start
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.android.ui.core.R
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.label.Label
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieClipSpec
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun LoadingSuccessScreen(model: LoadingSuccessBodyModel) {
  val composition by rememberLottieComposition(
    LottieCompositionSpec.RawRes(R.raw.loading_and_success),
    cacheKey = "loadingAndSuccess"
  )

  FormScreen(
    onBack = null,
    headerContent = {
      Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Start
      ) {
        LottieAnimation(
          modifier = Modifier.size(64.dp),
          composition = composition,
          clipSpec = LottieClipSpec.Progress(
            min = 0f,
            max = if (model.state is Success) 1f else 0.3f
          ),
          speed = if (model.state is Success) 1.5f else 1f,
          iterations = if (model.state is Success) 1 else LottieConstants.IterateForever
        )

        Spacer(modifier = Modifier.height(17.dp))

        // Always show the label regardless of if there's a message or not so that
        // the loading and success states line up
        Label(text = model.message ?: " ", type = LabelType.Title1)
      }
    }
  )
}

@Preview
@Composable
internal fun LoadingSuccessPreviewLoading() {
  PreviewWalletTheme {
    LoadingSuccessScreen(
      model =
        LoadingSuccessBodyModel(
          state = LoadingSuccessBodyModel.State.Loading,
          id = null
        )
    )
  }
}

@Preview
@Composable
internal fun LoadingSuccessPreviewSuccess() {
  PreviewWalletTheme {
    LoadingSuccessScreen(
      model =
        LoadingSuccessBodyModel(
          message = "You succeeded",
          state = Success,
          id = null
        )
    )
  }
}
