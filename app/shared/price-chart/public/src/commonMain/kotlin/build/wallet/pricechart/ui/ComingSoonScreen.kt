package build.wallet.pricechart.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.coming_soon
import build.wallet.statemachine.core.LabelModel
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.vectorResource

/**
 * Displays coming soon details and animated icon for Your balance screen.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun ComingSoonScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    if (LocalIsPreviewTheme.current) {
      Icon(
        imageVector = vectorResource(Res.drawable.coming_soon),
        contentDescription = null,
        modifier = Modifier.size(48.dp),
        tint = Color.Unspecified
      )
    } else {
      val loadingAnimationComposition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(
          Res.readBytes("files/stars.json").decodeToString()
        )
      }
      val painter = rememberLottiePainter(
        composition = loadingAnimationComposition,
        iterations = Compottie.IterateForever
      )
      Image(
        painter = painter,
        modifier = Modifier.size(48.dp),
        contentScale = ContentScale.FillBounds,
        contentDescription = null
      )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Label(
      model = LabelModel.StringModel("Coming soon..."),
      type = LabelType.Body3Regular,
      treatment = LabelTreatment.Disabled
    )
  }
}
