package build.wallet.ui.components.forms

import android.app.DatePickerDialog
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.util.Calendar

@Composable
actual fun NativeDatePickerDialog(
  initialDate: LocalDate?,
  minDate: LocalDate?,
  maxDate: LocalDate?,
  onDateSelected: (LocalDate) -> Unit,
  onDismiss: () -> Unit,
) {
  val baseContext = LocalContext.current
  val currentTheme = LocalTheme.current

  // Create a context that respects the app's theme override, not the system theme
  val themedContext = createThemedContext(baseContext, currentTheme)

  require(minDate == null || maxDate == null || minDate <= maxDate) {
    "minDate ($minDate) must be less than or equal to maxDate ($maxDate)"
  }

  val calendar = remember(initialDate, minDate, maxDate) {
    Calendar.getInstance().apply {
      initialDate?.let {
        // Validate initialDate is within bounds
        if (minDate != null && it < minDate) {
          set(minDate.year, minDate.monthNumber - 1, minDate.dayOfMonth)
        } else if (maxDate != null && it > maxDate) {
          set(maxDate.year, maxDate.monthNumber - 1, maxDate.dayOfMonth)
        } else {
          set(it.year, it.monthNumber - 1, it.dayOfMonth)
        }
      }
    }
  }

  val themeResId = themedContext.resources.getIdentifier(
    "BitkeyDatePickerDialog",
    "style",
    themedContext.packageName
  )

  val datePickerDialog = remember(calendar, minDate, maxDate) {
    DatePickerDialog(
      themedContext,
      themeResId, // Apply Bitkey theme with primary color
      { _, year, month, dayOfMonth ->
        // month is 0-based in Calendar, but 1-based in LocalDate
        val selectedDate = LocalDate(year, month + 1, dayOfMonth)
        onDateSelected(selectedDate)
        onDismiss()
      },
      calendar.get(Calendar.YEAR),
      calendar.get(Calendar.MONTH),
      calendar.get(Calendar.DAY_OF_MONTH)
    ).apply {
      minDate?.let {
        val minMillis = it.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        datePicker.minDate = minMillis
      }

      maxDate?.let {
        val maxMillis = it.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        datePicker.maxDate = maxMillis
      }

      setOnCancelListener {
        onDismiss()
      }

      setOnDismissListener {
        onDismiss()
      }
    }
  }

  DisposableEffect(Unit) {
    datePickerDialog.show()

    onDispose {
      // Dismiss the dialog if still showing when composable leaves composition
      if (datePickerDialog.isShowing) {
        datePickerDialog.dismiss()
      }
    }
  }
}

/**
 * Creates a context with the correct UI mode configuration based on the app's theme.
 * This ensures that dialogs and other components respect the app's theme override,
 * not the system theme.
 */
private fun createThemedContext(
  baseContext: android.content.Context,
  theme: Theme,
): android.content.Context {
  val config = Configuration(baseContext.resources.configuration)
  config.uiMode = when (theme) {
    Theme.LIGHT -> {
      // Clear night mode bit and set day mode
      (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
    }
    Theme.DARK -> {
      // Clear night mode bit and set night mode
      (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
    }
  }
  return ContextThemeWrapper(baseContext, 0).apply {
    applyOverrideConfiguration(config)
  }
}
