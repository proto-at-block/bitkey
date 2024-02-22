package build.wallet.ui.components.slider

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.amount.HeroAmount
import build.wallet.ui.components.card.Card
import build.wallet.ui.model.slider.AmountSliderModel

@Composable
fun AmountSliderCard(
  modifier: Modifier = Modifier,
  model: AmountSliderModel,
) {
  Card(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    val sliderValue = remember { mutableStateOf(model.value) }
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(
            vertical = 16.dp,
            horizontal = 8.dp
          ),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      HeroAmount(
        modifier = Modifier.padding(vertical = 16.dp),
        primaryAmount = AnnotatedString(model.primaryAmount),
        secondaryAmountWithCurrency = model.secondaryAmount
      )
      Slider(
        value = sliderValue.value,
        onValueChange = { newValue ->
          sliderValue.value = newValue
          model.onValueUpdate(sliderValue.value)
        },
        onValueChangeFinished = { model.onValueUpdate(sliderValue.value) },
        valueRange = model.valueRange,
        enabled = model.isEnabled
      )
    }
  }
}
