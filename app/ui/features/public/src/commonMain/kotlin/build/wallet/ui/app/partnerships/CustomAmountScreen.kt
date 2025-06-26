package build.wallet.ui.app.partnerships

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.amount.HeroAmount
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.keypad.Keypad
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun CustomAmountScreen(
  modifier: Modifier = Modifier,
  model: CustomAmountBodyModel,
) {
  val horizontalPadding = 20.dp
  FormScreen(
    modifier = modifier,
    onBack = model.onBack,
    horizontalPadding = 0,
    toolbarContent = {
      Toolbar(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.toolbar
      )
    },
    mainContent = {
      Column(
        modifier = Modifier.weight(1F)
      ) {
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
          contextLine = model.amountModel.secondaryAmount,
          onSwapClick = {},
          disabled = false
        )
        Spacer(Modifier.height(16.dp))

        Spacer(Modifier.weight(1F))

        Keypad(
          showDecimal = model.keypadModel.showDecimal,
          onButtonPress = model.keypadModel.onButtonPress
        )
      }
    },
    footerContent = {
      Button(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.primaryButton
      )
    }
  )
}
