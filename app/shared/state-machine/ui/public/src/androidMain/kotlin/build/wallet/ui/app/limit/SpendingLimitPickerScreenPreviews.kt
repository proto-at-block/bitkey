package build.wallet.ui.app.limit

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.limit.SpendingLimitsCopy
import build.wallet.statemachine.limit.picker.EntryMode
import build.wallet.statemachine.limit.picker.SpendingLimitPickerModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.ui.model.slider.AmountSliderModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun PreviewSpendingLimitPickerScreenNoValue() {
  PreviewWalletTheme {
    SpendingLimitPickerScreen(
      model = SpendingLimitPickerModel(
        onBack = {},
        toolbarModel = ToolbarModel(leadingAccessory = BackAccessory {}),
        entryMode = EntryMode.Slider(
          sliderModel = AmountSliderModel(
            primaryAmount = "$0",
            secondaryAmount = "0 sats",
            value = 0f,
            valueRange = 0f..200f,
            onValueUpdate = {},
            isEnabled = true
          )
        ),
        spendingLimitsCopy = SpendingLimitsCopy.get(isRevampOn = false),
        setLimitButtonEnabled = false,
        setLimitButtonLoading = false,
        onSetLimitClick = {}
      )
    )
  }
}

@Preview
@Composable
fun PreviewSpendingLimitPickerScreenWithValue() {
  PreviewWalletTheme {
    SpendingLimitPickerScreen(
      model = SpendingLimitPickerModel(
        onBack = {},
        toolbarModel = ToolbarModel(leadingAccessory = BackAccessory {}),
        entryMode = EntryMode.Slider(
          sliderModel = AmountSliderModel(
            primaryAmount = "$100",
            secondaryAmount = "484,191 sats",
            value = 0f,
            valueRange = 0f..200f,
            onValueUpdate = {},
            isEnabled = true
          )
        ),
        spendingLimitsCopy = SpendingLimitsCopy.get(isRevampOn = false),
        setLimitButtonEnabled = true,
        setLimitButtonLoading = false,
        onSetLimitClick = {}
      )
    )
  }
}

@Preview
@Composable
fun PreviewSpendingLimitPickerScreenNoValueKeypad() {
  PreviewWalletTheme {
    SpendingLimitPickerScreen(
      model = SpendingLimitPickerModel(
        onBack = {},
        toolbarModel = ToolbarModel(
          leadingAccessory = BackAccessory {},
          middleAccessory = ToolbarMiddleAccessoryModel(title = "Set daily limit")
        ),
        entryMode = EntryMode.Keypad(
          amountModel = MoneyAmountEntryModel(
            primaryAmount = "$0.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "0 sats"
          ),
          keypadModel = KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          )
        ),
        spendingLimitsCopy = SpendingLimitsCopy.get(isRevampOn = true),
        setLimitButtonEnabled = false,
        setLimitButtonLoading = false,
        onSetLimitClick = {}
      )
    )
  }
}

@Preview
@Composable
fun PreviewSpendingLimitPickerScreenWithValueKeypad() {
  PreviewWalletTheme {
    SpendingLimitPickerScreen(
      model = SpendingLimitPickerModel(
        onBack = {},
        toolbarModel = ToolbarModel(
          leadingAccessory = BackAccessory {},
          middleAccessory = ToolbarMiddleAccessoryModel(title = "Set daily limit")
        ),
        entryMode = EntryMode.Keypad(
          amountModel = MoneyAmountEntryModel(
            primaryAmount = "$100",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "484,191 sats"
          ),
          keypadModel = KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          )
        ),
        spendingLimitsCopy = SpendingLimitsCopy.get(isRevampOn = true),
        setLimitButtonEnabled = true,
        setLimitButtonLoading = false,
        onSetLimitClick = {}
      )
    )
  }
}
