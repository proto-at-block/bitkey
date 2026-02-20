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
import build.wallet.statemachine.limit.picker.SpendingLimitPickerModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.amount.HeroAmount
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.keypad.Keypad
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

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
    mainContent = {
      Spacer(Modifier.weight(1F))

      HeroAmount(
        modifier =
          Modifier.align(CenterHorizontally)
            .padding(horizontal = 20.dp)
            .clipToBounds(),
        primaryAmount =
          buildAnnotatedString {
            append(model.amountModel.primaryAmount)
            model.amountModel.primaryAmountGhostedSubstringRange?.let { substringRange ->
              addStyle(
                style = SpanStyle(color = WalletTheme.colors.foreground30),
                start = substringRange.first,
                end = substringRange.last + 1
              )
            }
          },
        primaryAmountLabelType = LabelType.Display1,
        contextLine = model.amountModel.secondaryAmount
      )

      Spacer(Modifier.weight(1F))

      Keypad(
        showDecimal = model.keypadModel.showDecimal,
        onButtonPress = model.keypadModel.onButtonPress
      )
    },
    footerContent = {
      Button(model.setLimitButtonModel)
    }
  )
}
