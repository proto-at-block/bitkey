package build.wallet.ui.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.radio.RadioButton
import build.wallet.ui.model.list.ListItemPickerMenu
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
internal fun <Option : Any> ListItemPickerMenu(model: ListItemPickerMenu<Option>) {
  Dialog(
    onDismissRequest = model.onDismiss
  ) {
    Column(
      modifier = Modifier
        .background(
          color = WalletTheme.colors.containerBackground,
          shape = RoundedCornerShape(16.dp)
        )
        .border(
          width = 1.dp,
          color = WalletTheme.colors.foreground10,
          shape = RoundedCornerShape(16.dp)
        )
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
            selectedColor = WalletTheme.colors.bitkeyPrimary
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
