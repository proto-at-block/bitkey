package build.wallet.ui.components.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.model.datetime.DatePickerModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import androidx.compose.material3.DatePicker as MaterialDatePicker
import androidx.compose.material3.DatePickerDialog as MaterialDatePickerDialog

@Composable
fun DatePickerField(
  modifier: Modifier = Modifier,
  model: DatePickerModel,
) {
  var isShowingDatePicker by remember {
    mutableStateOf(false)
  }

  val textStyle =
    WalletTheme.labelStyle(
      type = LabelType.Body2Regular,
      treatment = LabelTreatment.Primary
    )

  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(size = 32.dp))
        .defaultMinSize(
          minWidth = TextFieldDefaults.MinWidth,
          minHeight = TextFieldDefaults.MinHeight
        )
        .background(color = WalletTheme.colors.foreground10)
        .clickable {
          isShowingDatePicker = true
        },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      modifier = Modifier.padding(horizontal = 16.dp),
      text = model.valueStringRepresentation,
      style = textStyle
    )
  }

  if (isShowingDatePicker) {
    DatePickerDialog(
      initialDate = model.value,
      onDateSelected = model.onValueChange,
      onDismiss = {
        isShowingDatePicker = false
      }
    )
  }
}

@Composable
private fun DatePickerDialog(
  initialDate: LocalDate?,
  onDateSelected: (LocalDate) -> Unit,
  onDismiss: () -> Unit,
) {
  val datePickerState =
    rememberDatePickerState(
      initialSelectedDateMillis = initialDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
    )

  MaterialDatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      Button(onClick = {
        datePickerState.selectedDateMillis?.let { millis ->
          val selectedDate =
            Instant.fromEpochMilliseconds(millis)
              .toLocalDateTime(TimeZone.UTC)
          onDateSelected(selectedDate.date)
          onDismiss()
        }
      }) {
        Text("Confirm")
      }
    }
  ) {
    MaterialDatePicker(
      state = datePickerState
    )
  }
}

@Preview
@Composable
internal fun DatePickerFieldWithNoValuePreview() {
  PreviewWalletTheme {
    DatePickerField(
      model =
        DatePickerModel(
          valueStringRepresentation = "",
          value = null,
          onValueChange = { }
        )
    )
  }
}

@Preview
@Composable
internal fun DatePickerFieldWithNowPreview() {
  PreviewWalletTheme {
    DatePickerField(
      model =
        DatePickerModel(
          valueStringRepresentation = "February 8, 2024",
          value = LocalDate(2024, 2, 8),
          onValueChange = { }
        )
    )
  }
}
