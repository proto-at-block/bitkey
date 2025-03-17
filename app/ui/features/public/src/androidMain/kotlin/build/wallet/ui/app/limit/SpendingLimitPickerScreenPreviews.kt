package build.wallet.ui.app.limit

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.limit.picker.EntryMode
import build.wallet.statemachine.limit.picker.SpendingLimitPickerModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tooling.PreviewWalletTheme

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
        setLimitButtonEnabled = true,
        setLimitButtonLoading = false,
        onSetLimitClick = {}
      )
    )
  }
}
