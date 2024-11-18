package build.wallet.ui.components.forms

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.ui.model.datetime.DatePickerModel
import build.wallet.ui.tooling.PreviewWalletTheme
import kotlinx.datetime.LocalDate

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
