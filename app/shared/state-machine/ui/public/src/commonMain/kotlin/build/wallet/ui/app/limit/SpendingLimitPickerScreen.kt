package build.wallet.ui.app.limit

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.limit.SpendingLimitsCopy
import build.wallet.statemachine.limit.picker.EntryMode
import build.wallet.statemachine.limit.picker.SpendingLimitPickerModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.amount.HeroAmount
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.keypad.Keypad
import build.wallet.ui.components.slider.AmountSliderCard
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.slider.AmountSliderModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun SpendingLimitPickerScreen(
  modifier: Modifier = Modifier,
  model: SpendingLimitPickerModel,
) {
  FormScreen(
    modifier = modifier,
    onBack = model.onBack,
    toolbarContent = {
      Toolbar(model.toolbarModel)
    },
    headerContent = {
      model.headerModel?.let {
        Header(model = it)
      }
    },
    mainContent = {
      when (val entryMode = model.entryMode) {
        is EntryMode.Slider -> {
          AmountSliderCard(model = entryMode.sliderModel)
        }
        is EntryMode.Keypad -> {
          Spacer(Modifier.weight(1F))

          HeroAmount(
            modifier =
              Modifier.align(CenterHorizontally)
                .padding(horizontal = 20.dp)
                .clipToBounds(),
            primaryAmount =
              buildAnnotatedString {
                append(entryMode.amountModel.primaryAmount)
                entryMode.amountModel.primaryAmountGhostedSubstringRange?.let { substringRange ->
                  addStyle(
                    style = SpanStyle(color = WalletTheme.colors.foreground30),
                    start = substringRange.first,
                    end = substringRange.last + 1
                  )
                }
              },
            primaryAmountLabelType = LabelType.Display1,
            secondaryAmountWithCurrency = entryMode.amountModel.secondaryAmount
          )

          Spacer(Modifier.weight(1F))

          Keypad(
            showDecimal = entryMode.keypadModel.showDecimal,
            onButtonPress = entryMode.keypadModel.onButtonPress
          )
        }
      }
    },
    footerContent = {
      Button(model.setLimitButtonModel)
    }
  )
}

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
