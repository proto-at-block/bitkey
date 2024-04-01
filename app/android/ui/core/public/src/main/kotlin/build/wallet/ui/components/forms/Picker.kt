package build.wallet.ui.components.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.model.picker.ItemPickerModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.ImmutableList

@Composable
fun <Option : Any> ItemPickerField(
  modifier: Modifier = Modifier,
  model: ItemPickerModel<Option>,
) {
  var isShowingItemPicker by remember {
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
          isShowingItemPicker = true
        },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      modifier = Modifier.padding(horizontal = 16.dp),
      text = model.titleSelector(model.selectedOption),
      style = textStyle
    )
  }

  if (isShowingItemPicker) {
    ItemPickerDialog(
      selectedOption = model.selectedOption,
      options = model.options,
      titleSelector = model.titleSelector,
      onOptionSelected = { option ->
        isShowingItemPicker = false
        model.onOptionSelected(option)
      },
      onDismiss = {
        isShowingItemPicker = false
      }
    )
  }
}

@Composable
private fun <Option : Any> ItemPickerDialog(
  selectedOption: Option?,
  options: ImmutableList<Option>,
  titleSelector: (Option) -> String,
  onOptionSelected: (Option) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.background(WalletTheme.colors.background)
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      options.forEach { option ->
        Row(
          Modifier
            .fillMaxWidth()
            .selectable(
              selected = selectedOption == option,
              onClick = { onOptionSelected(option) }
            )
            .padding(vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(
            selected = selectedOption == option,
            onClick = { onOptionSelected(option) },
            colors =
              RadioButtonDefaults.colors(
                selectedColor = WalletTheme.colors.primary
              )
          )
          Label(
            text = titleSelector(option),
            type = LabelType.Body3Regular
          )
        }
      }
    }
  }
}
