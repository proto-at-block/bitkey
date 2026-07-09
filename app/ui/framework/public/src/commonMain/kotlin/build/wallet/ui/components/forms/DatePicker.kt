package build.wallet.ui.components.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.compose.datePickerTestTag
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.resolveTestTag
import build.wallet.ui.model.datetime.DatePickerModel
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun DatePickerField(
  modifier: Modifier = Modifier,
  model: DatePickerModel,
  testTag: String? = null,
) {
  var isShowingDatePicker by remember {
    mutableStateOf(false)
  }
  val backgroundColor =
    when (LocalTheme.current) {
      Theme.LIGHT -> WalletTheme.colors.subtleBackground
      else -> WalletTheme.colors.foreground10
    }

  Row(
    modifier =
      modifier
        .resId(
          resolveTestTag(
            testTag ?: model.testTag,
            datePickerTestTag(model.valueStringRepresentation)
          )
        )
        .clip(
          RoundedCornerShape(
            size = 8.dp
          )
        )
        .defaultMinSize(
          minWidth = 280.dp, // Material3 default TextField minimum width
          minHeight = 56.dp // Material3 default TextField minimum height
        )
        .background(color = backgroundColor)
        .clickable {
          isShowingDatePicker = true
        },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Label(
      modifier = Modifier.padding(horizontal = 16.dp),
      text = model.valueStringRepresentation,
      type = LabelType.Body2Regular,
      treatment = LabelTreatment.Primary
    )
  }

  if (isShowingDatePicker) {
    NativeDatePickerDialog(
      initialDate = model.value,
      minDate = model.minDate,
      maxDate = model.maxDate,
      onDateSelected = model.onValueChange,
      onDismiss = {
        isShowingDatePicker = false
      }
    )
  }
}
