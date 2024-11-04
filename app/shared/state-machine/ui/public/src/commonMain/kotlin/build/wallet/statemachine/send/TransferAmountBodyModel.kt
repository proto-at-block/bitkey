package build.wallet.statemachine.send

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import dev.zacsweers.redacted.annotations.Redacted

data class TransferAmountBodyModel(
  override val onBack: () -> Unit,
  @Redacted
  val toolbar: ToolbarModel, // Balance is exposed in this model so redacting it
  val amountModel: MoneyAmountEntryModel,
  val keypadModel: KeypadModel,
  val primaryButton: ButtonModel,
  val cardModel: CardModel?,
  val amountDisabled: Boolean,
  val onSwapCurrencyClick: () -> Unit,
  // We don't want to track this for privacy reasons
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  constructor(
    onBack: () -> Unit,
    balanceTitle: String,
    amountModel: MoneyAmountEntryModel,
    keypadModel: KeypadModel,
    cardModel: CardModel?,
    continueButtonEnabled: Boolean,
    amountDisabled: Boolean,
    onContinueClick: () -> Unit,
    onSwapCurrencyClick: () -> Unit,
  ) : this(
    onBack = onBack,
    toolbar =
      ToolbarModel(
        leadingAccessory = BackAccessory(onClick = onBack),
        middleAccessory =
          ToolbarMiddleAccessoryModel(
            title = "Amount",
            subtitle = balanceTitle
          )
      ),
    amountModel = amountModel,
    keypadModel = keypadModel,
    primaryButton =
      ButtonModel(
        text = "Continue",
        isEnabled = continueButtonEnabled,
        size = Footer,
        onClick = StandardClick(onContinueClick)
      ),
    cardModel = cardModel,
    amountDisabled = amountDisabled,
    onSwapCurrencyClick = onSwapCurrencyClick
  )
}
