package build.wallet.ui.app.core

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment.Companion.Start
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.loader_static
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.label.Label
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import io.github.alexzhirkevich.compottie.*
import org.jetbrains.compose.resources.vectorResource

@Composable
fun LoadingSuccessScreen(
  modifier: Modifier = Modifier,
  model: LoadingSuccessBodyModel,
) {
  val loadingAnimationComposition by rememberLottieComposition {
    LottieCompositionSpec.JsonString(
      Res.readBytes("files/loading_and_success.json").decodeToString()
    )
  }
  val painter = rememberLottiePainter(
    composition = loadingAnimationComposition,
    iterations = if (model.state is Success) 1 else Compottie.IterateForever,
    speed = if (model.state is Success) 1.5f else 1f,
    clipSpec = LottieClipSpec.Progress(
      min = 0f,
      max = if (model.state is Success) 1f else 0.3f
    )
  )

  FormScreen(
    modifier = modifier,
    onBack = null,
    headerContent = {
      Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Start
      ) {
        if (LocalIsPreviewTheme.current) {
          Image(
            imageVector = vectorResource(Res.drawable.loader_static),
            contentDescription = null,
            modifier = Modifier.size(64.dp)
          )
        } else {
          Image(
            painter = painter,
            modifier = Modifier.size(64.dp),
            contentScale = ContentScale.FillBounds,
            contentDescription = null
          )
        }

        Spacer(modifier = Modifier.height(17.dp))

        // Always show the label regardless of if there's a message or not so that
        // the loading and success states line up
        Label(text = model.message ?: " ", type = LabelType.Title1)
      }
    }
  )
}
