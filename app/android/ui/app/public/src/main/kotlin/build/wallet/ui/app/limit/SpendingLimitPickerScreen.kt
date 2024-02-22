package build.wallet.ui.app.limit

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.limit.picker.SpendingLimitPickerModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.slider.AmountSliderCard
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.slider.AmountSliderModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun SpendingLimitPickerScreen(model: SpendingLimitPickerModel) {
  FormScreen(
    onBack = model.onBack,
    toolbarContent = {
      Toolbar(model.toolbarModel)
    },
    headerContent = {
      Header(model = model.headerModel)
    },
    mainContent = {
      AmountSliderCard(model = model.limitSliderModel)
    },
    footerContent = {
      Button(model.setLimitButtonModel)
    }
  )
}

@Preview
@Composable
internal fun PreviewSpendingLimitPickerScreenNoValue() {
  PreviewWalletTheme {
    SpendingLimitPickerScreen(
      SpendingLimitPickerModel(
        onBack = {},
        toolbarModel = ToolbarModel(leadingAccessory = BackAccessory {}),
        limitSliderModel =
          AmountSliderModel(
            primaryAmount = "$0",
            secondaryAmount = "0 sats",
            value = 0f,
            valueRange = 0f..200f,
            onValueUpdate = {},
            isEnabled = true
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
internal fun PreviewSpendingLimitPickerScreenWithValue() {
  PreviewWalletTheme {
    SpendingLimitPickerScreen(
      SpendingLimitPickerModel(
        onBack = {},
        toolbarModel = ToolbarModel(leadingAccessory = BackAccessory {}),
        limitSliderModel =
          AmountSliderModel(
            primaryAmount = "$100",
            secondaryAmount = "484,191 sats",
            value = 0f,
            valueRange = 0f..200f,
            onValueUpdate = {},
            isEnabled = true
          ),
        setLimitButtonEnabled = true,
        setLimitButtonLoading = false,
        onSetLimitClick = {}
      )
    )
  }
}
