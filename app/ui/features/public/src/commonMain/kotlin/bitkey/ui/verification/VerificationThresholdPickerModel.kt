package bitkey.ui.verification

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.TxVerificationEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.money.calculator.MoneyCalculatorModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.amount.HeroAmount
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.keypad.Keypad
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

/**
 * Model for entering a currency amount for verification limits.
 */
data class VerificationThresholdPickerModel(
  override val onBack: () -> Unit,
  val onConfirmClick: () -> Unit,
  val model: MoneyCalculatorModel,
) : BodyModel() {
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = EventTrackerScreenInfo(
    eventTrackerScreenId = TxVerificationEventTrackerScreenId.SET_AMOUNT
  )

  @Composable
  override fun render(modifier: Modifier) {
    FormScreen(
      modifier = modifier,
      onBack = onBack,
      toolbarContent = {
        Toolbar(
          model = ToolbarModel(
            leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onBack),
            middleAccessory = ToolbarMiddleAccessoryModel(
              title = "Custom amount"
            )
          )
        )
      },
      mainContent = {
        Spacer(Modifier.Companion.weight(1F))

        HeroAmount(
          modifier =
            Modifier.Companion.align(Alignment.Companion.CenterHorizontally)
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
          contextLine = "Verify above this amount"
        )

        Spacer(Modifier.Companion.weight(1F))

        Keypad(
          showDecimal = model.keypadModel.showDecimal,
          onButtonPress = model.keypadModel.onButtonPress
        )
      },
      footerContent = {
        Button(
          model = ButtonModel(
            text = "Confirm",
            onClick = StandardClick { onConfirmClick() },
            leadingIcon = Icon.SmallIconBitkey,
            treatment = ButtonModel.Treatment.BitkeyInteraction,
            size = ButtonModel.Size.Footer,
            isEnabled = !model.primaryAmount.isZero
          )
        )
      }
    )
  }
}
