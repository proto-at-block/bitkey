package build.wallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.ui.components.slider.Slider

@Preview(locale = "en", showBackground = true)
@Composable
fun PreviewSlider() {
  val value = remember { mutableStateOf(.5f) }
  Slider(
    value = value.value,
    onValueChange = { f -> value.value = f },
    enabled = true,
    valueRange = 0f..1f
  )
}

@Preview(locale = "ar", showBackground = true)
@Composable
fun PreviewSliderRTL() {
  val value = remember { mutableStateOf(.5f) }
  Slider(
    value = value.value,
    onValueChange = { f -> value.value = f },
    enabled = true,
    valueRange = 0f..1f
  )
}
