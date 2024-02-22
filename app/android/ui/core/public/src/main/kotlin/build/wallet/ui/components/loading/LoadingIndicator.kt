package build.wallet.ui.components.loading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import build.wallet.android.ui.core.R
import build.wallet.ui.theme.WalletTheme
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

@Composable
fun LoadingIndicator(
  modifier: Modifier = Modifier,
  color: Color = WalletTheme.colors.foreground,
) {
  val loadingAnimationComposition by rememberLottieComposition(
    LottieCompositionSpec.RawRes(R.raw.loading)
  )

  // Apply the given color to the lottie animation
  // Note: this only works on Android
  val dynamicProperties =
    rememberLottieDynamicProperties(
      rememberLottieDynamicProperty(
        property = LottieProperty.COLOR_FILTER,
        value = SimpleColorFilter(color.toArgb()),
        keyPath = arrayOf("**")
      )
    )

  LottieAnimation(
    modifier = modifier,
    composition = loadingAnimationComposition,
    iterations = LottieConstants.IterateForever,
    dynamicProperties = dynamicProperties
  )
}
