package build.wallet.statemachine.partnerships.purchase

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.DepositEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.ui.app.partnerships.CustomAmountScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class CustomAmountBodyModel(
  override val onBack: () -> Unit,
  val toolbar: ToolbarModel,
  val amountModel: MoneyAmountEntryModel,
  val keypadModel: KeypadModel,
  val primaryButton: ButtonModel,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  constructor(
    onBack: () -> Unit,
    limits: String,
    amountModel: MoneyAmountEntryModel,
    keypadModel: KeypadModel,
    continueButtonEnabled: Boolean,
    onNext: () -> Unit,
  ) : this(
    eventTrackerScreenInfo = EventTrackerScreenInfo(DepositEventTrackerScreenId.CUSTOM_PARTNER_PURCHASE_AMOUNT),
    onBack = onBack,
    toolbar =
      ToolbarModel(
        leadingAccessory = BackAccessory(onClick = onBack),
        middleAccessory =
          ToolbarMiddleAccessoryModel(
            title = "Choose an amount",
            subtitle = limits
          )
      ),
    amountModel = amountModel,
    keypadModel = keypadModel,
    primaryButton =
      ButtonModel(
        text = "Next",
        isEnabled = continueButtonEnabled,
        size = Footer,
        onClick = StandardClick(onNext)
      )
  )

  @Composable
  override fun render(modifier: Modifier) {
    CustomAmountScreen(modifier, model = this)
  }
}
