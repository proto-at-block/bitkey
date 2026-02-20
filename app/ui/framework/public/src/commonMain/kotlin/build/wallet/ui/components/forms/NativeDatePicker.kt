package build.wallet.ui.components.forms

import androidx.compose.runtime.Composable
import kotlinx.datetime.LocalDate

/**
 * Platform-native date picker dialog.
 *
 * - Android: Uses DatePickerDialog from Android framework
 * - iOS: Uses UIDatePicker in a UIKit bottom sheet
 * - JVM/Desktop: Fallback implementation
 *
 * @param initialDate The initial date to display, or null for current date
 * @param minDate The minimum selectable date (inclusive), or null for no minimum
 * @param maxDate The maximum selectable date (inclusive), or null for no maximum
 * @param onDateSelected Callback when a date is selected
 * @param onDismiss Callback when the dialog is dismissed
 *
 * @throws IllegalArgumentException if minDate > maxDate
 */
@Composable
expect fun NativeDatePickerDialog(
  initialDate: LocalDate?,
  minDate: LocalDate? = null,
  maxDate: LocalDate? = null,
  onDateSelected: (LocalDate) -> Unit,
  onDismiss: () -> Unit,
)
