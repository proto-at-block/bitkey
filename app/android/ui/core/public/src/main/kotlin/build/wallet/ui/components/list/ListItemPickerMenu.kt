package build.wallet.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.list.ListItemPickerMenu
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
internal fun <Option : Any> ListItemPickerMenu(model: ListItemPickerMenu<Option>) {
  BasicAlertDialog(
    onDismissRequest = model.onDismiss,
    modifier = Modifier.background(WalletTheme.colors.background)
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      model.options.forEach { option ->
        Row(
          Modifier
            .fillMaxWidth()
            .selectable(
              selected = model.selectedOption == option,
              onClick = { model.onOptionSelected(option) }
            )
            .padding(vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(
            selected = model.selectedOption == option,
            onClick = { model.onOptionSelected(option) },
            colors =
              RadioButtonDefaults.colors(
                selectedColor = WalletTheme.colors.bitkeyPrimary
              )
          )
          Label(
            text = model.titleSelector(option),
            type = LabelType.Body3Regular
          )
        }
      }
    }
  }
}
