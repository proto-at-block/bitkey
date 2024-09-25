package build.wallet.statemachine.limit.picker

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.MobilePayEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.limit.SpendingLimitsCopy
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.slider.AmountSliderModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Spending limit picker model.
 */
data class SpendingLimitPickerModel(
  override val onBack: () -> Unit,
  val toolbarModel: ToolbarModel,
  val headerModel: FormHeaderModel?,
  val entryMode: EntryMode,
  val setLimitButtonModel: ButtonModel,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = MobilePayEventTrackerScreenId.MOBILE_PAY_LIMIT_UPDATE_SLIDER
    ),
) : BodyModel() {
  constructor(
    onBack: () -> Unit,
    toolbarModel: ToolbarModel,
    entryMode: EntryMode,
    spendingLimitsCopy: SpendingLimitsCopy,
    setLimitButtonEnabled: Boolean,
    setLimitButtonLoading: Boolean,
    onSetLimitClick: () -> Unit,
  ) : this(
    onBack = onBack,
    toolbarModel = toolbarModel,
    headerModel = when (entryMode) {
      is EntryMode.Slider -> FormHeaderModel(
        headline = "Set a daily limit",
        subline = "Total daily transactions below this amount won’t need your Bitkey device."
      )
      else -> null
    },
    entryMode = entryMode,
    setLimitButtonModel =
      BitkeyInteractionButtonModel(
        text = spendingLimitsCopy.setDailyLimitCta,
        size = Footer,
        isEnabled = setLimitButtonEnabled,
        isLoading = setLimitButtonLoading,
        onClick = StandardClick(onSetLimitClick)
      )
  )
}

sealed class EntryMode {
  data class Slider(
    val sliderModel: AmountSliderModel,
  ) : EntryMode()

  data class Keypad(
    val amountModel: MoneyAmountEntryModel,
    val keypadModel: KeypadModel,
  ) : EntryMode()
}
