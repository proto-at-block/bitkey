package build.wallet.ui.components.forms

import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Desktop/JVM implementation backed by the Compose Multiplatform material3
 * [DatePicker]. This is not an OS-native picker (the JVM target has no native
 * date control), but it renders a functional, themed calendar dialog and
 * invokes [onDateSelected] with the chosen [LocalDate] just like the Android
 * and iOS actuals.
 *
 * Dates are interpreted in [TimeZone.UTC] to match how [minDate]/[maxDate] are
 * supplied to the Android actual, keeping selection bounds consistent across
 * platforms regardless of the host machine's local time zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun NativeDatePickerDialog(
  initialDate: LocalDate?,
  minDate: LocalDate?,
  maxDate: LocalDate?,
  onDateSelected: (LocalDate) -> Unit,
  onDismiss: () -> Unit,
) {
  require(minDate == null || maxDate == null || minDate <= maxDate) {
    "minDate ($minDate) must be less than or equal to maxDate ($maxDate)"
  }

  // Clamp the initial selection into the allowed range so the picker opens on a selectable day,
  // mirroring the bounds handling in the Android/iOS actuals. If no date is supplied, default to
  // today in UTC so confirming without an explicit selection still returns a date.
  val clampedInitialDate = remember(initialDate, minDate, maxDate) {
    val defaultInitialDate = initialDate ?: Clock.System.now().toLocalDateTime(TimeZone.UTC).date
    defaultInitialDate.clampedTo(minDate = minDate, maxDate = maxDate)
  }

  val selectableDates = remember(minDate, maxDate) {
    object : SelectableDates {
      private val minMillis = minDate?.toUtcEpochMillis()
      private val maxMillis = maxDate?.toUtcEpochMillis()

      override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        if (minMillis != null && utcTimeMillis < minMillis) return false
        if (maxMillis != null && utcTimeMillis > maxMillis) return false
        return true
      }
    }
  }

  val datePickerState = rememberDatePickerState(
    initialSelectedDateMillis = clampedInitialDate.toUtcEpochMillis(),
    selectableDates = selectableDates
  )

  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          val selectedDate = datePickerState.selectedDateMillis?.toUtcLocalDate()
            ?: clampedInitialDate
          onDateSelected(selectedDate)
          onDismiss()
        }
      ) {
        BasicText("Confirm")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        BasicText("Cancel")
      }
    },
    colors = DatePickerDefaults.colors()
  ) {
    DatePicker(state = datePickerState)
  }
}

private fun LocalDate.toUtcEpochMillis(): Long =
  atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toUtcLocalDate(): LocalDate =
  Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

private fun LocalDate.clampedTo(
  minDate: LocalDate?,
  maxDate: LocalDate?,
): LocalDate =
  when {
    minDate != null && this < minDate -> minDate
    maxDate != null && this > maxDate -> maxDate
    else -> this
  }
