package build.wallet.ui.components.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.radio.RadioButton
import build.wallet.ui.compose.itemPickerOptionTestTag
import build.wallet.ui.compose.itemPickerTestTag
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.resolveTestTag
import build.wallet.ui.model.picker.ItemPickerModel
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.ImmutableList

@Composable
fun <Option : Any> ItemPickerField(
  modifier: Modifier = Modifier,
  model: ItemPickerModel<Option>,
  testTag: String? = null,
) {
  var isShowingItemPicker by remember {
    mutableStateOf(false)
  }
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val backgroundColor =
    when {
      !isDesignSystemV2Enabled -> WalletTheme.colors.foreground10
      LocalTheme.current == Theme.LIGHT -> WalletTheme.colors.subtleBackground
      else -> WalletTheme.colors.foreground10
    }

  Row(
    modifier =
      modifier
        .resId(
          resolveTestTag(
            testTag ?: model.testTag,
            itemPickerTestTag(model.titleSelector(model.selectedOption))
          )
        )
        .clip(
          RoundedCornerShape(
            size = if (isDesignSystemV2Enabled) 8.dp else 32.dp
          )
        )
        .defaultMinSize(
          minWidth = 280.dp, // Material3 default TextField minimum width
          minHeight = 56.dp // Material3 default TextField minimum height
        )
        .background(color = backgroundColor)
        .clickable {
          isShowingItemPicker = true
        },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Label(
      modifier = Modifier.padding(horizontal = 16.dp),
      text = model.titleSelector(model.selectedOption),
      type = LabelType.Body2Regular,
      treatment = LabelTreatment.Primary
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
  Dialog(
    onDismissRequest = onDismiss
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
      options.forEach { option ->
        val optionTitle = titleSelector(option)
        Row(
          Modifier
            .resId(itemPickerOptionTestTag(optionTitle))
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
            selectedColor = WalletTheme.colors.bitkeyPrimary,
            testTag = itemPickerOptionTestTag("$optionTitle radio")
          )
          Label(
            text = optionTitle,
            type = LabelType.Body3Regular
          )
        }
      }
    }
  }
}
