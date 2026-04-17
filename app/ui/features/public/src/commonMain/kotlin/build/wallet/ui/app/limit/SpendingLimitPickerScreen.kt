package build.wallet.ui.app.limit

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.limit.picker.SpendingLimitPickerModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.amount.AnimatedHeroAmount
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.keypad.Keypad
import build.wallet.ui.components.toolbar.AmountEntryToolbar
import build.wallet.ui.components.toolbar.amountEntryBackgroundColor
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.tokens.LabelType

@Composable
fun SpendingLimitPickerScreen(
  modifier: Modifier = Modifier,
  model: SpendingLimitPickerModel,
) {
  val horizontalPadding = 20.dp
  val isDesignSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  FormScreen(
    modifier = modifier,
    onBack = model.onBack,
    background = amountEntryBackgroundColor(),
    horizontalPadding = 0,
    toolbarContent = {
      AmountEntryToolbar(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.toolbarModel
      )
    },
    mainContent = {
      Spacer(Modifier.weight(1F))

      AnimatedHeroAmount(
        modifier =
          Modifier.align(CenterHorizontally)
            .padding(horizontal = 20.dp)
            .clipToBounds(),
        primaryAmount = model.amountModel.primaryAmount,
        primaryAmountGhostedSubstringRange = model.amountModel.primaryAmountGhostedSubstringRange,
        primaryAmountLabelType = LabelType.Display1,
        contextLine = model.amountModel.secondaryAmount,
        centerWhenDesignSystemV2 = true
      )

      Spacer(Modifier.height(16.dp))

      Spacer(Modifier.weight(1F))

      Keypad(
        modifier =
          Modifier.padding(horizontal = if (isDesignSystemV2Enabled) horizontalPadding else 0.dp),
        showDecimal = model.keypadModel.showDecimal,
        onButtonPress = model.keypadModel.onButtonPress
      )
    },
    footerContent = {
      Button(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.setLimitButtonModel
      )
    }
  )
}
