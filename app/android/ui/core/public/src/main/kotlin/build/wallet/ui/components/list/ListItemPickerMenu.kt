package build.wallet.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <Option : Any> ListItemPickerMenu(model: ListItemPickerMenu<Option>) {
  AlertDialog(
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
                selectedColor = WalletTheme.colors.primary
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
