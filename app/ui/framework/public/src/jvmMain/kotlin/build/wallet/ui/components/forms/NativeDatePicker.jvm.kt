package build.wallet.ui.components.forms

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

@Composable
actual fun NativeDatePickerDialog(
  initialDate: LocalDate?,
  minDate: LocalDate?,
  maxDate: LocalDate?,
  onDateSelected: (LocalDate) -> Unit,
  onDismiss: () -> Unit,
) {
  // Validate date constraints
  require(minDate == null || maxDate == null || minDate <= maxDate) {
    "minDate ($minDate) must be less than or equal to maxDate ($maxDate)"
  }

  // Desktop/JVM implementation placeholder
  // For now, immediately dismiss as desktop isn't a primary platform
  onDismiss()
}
