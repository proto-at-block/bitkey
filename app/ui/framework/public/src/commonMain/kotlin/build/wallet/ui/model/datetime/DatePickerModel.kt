package build.wallet.ui.model.datetime

import kotlinx.datetime.LocalDate

data class DatePickerModel(
  val valueStringRepresentation: String,
  val value: LocalDate?,
  val onValueChange: (LocalDate) -> Unit,
)
