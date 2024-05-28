package build.wallet.statemachine.limit.picker

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.MobilePayEventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
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
  val headerModel: FormHeaderModel,
  val limitSliderModel: AmountSliderModel,
  val setLimitButtonModel: ButtonModel,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? =
    EventTrackerScreenInfo(
      eventTrackerScreenId = MobilePayEventTrackerScreenId.MOBILE_PAY_LIMIT_UPDATE_SLIDER
    ),
) : BodyModel() {
  constructor(
    onBack: () -> Unit,
    toolbarModel: ToolbarModel,
    limitSliderModel: AmountSliderModel,
    setLimitButtonEnabled: Boolean,
    setLimitButtonLoading: Boolean,
    onSetLimitClick: () -> Unit,
  ) : this(
    onBack = onBack,
    toolbarModel = toolbarModel,
    headerModel =
      FormHeaderModel(
        headline = "Set a daily limit",
        subline = "Total daily transactions below this amount won’t need your Bitkey device."
      ),
    limitSliderModel = limitSliderModel,
    setLimitButtonModel =
      BitkeyInteractionButtonModel(
        text = "Set limit",
        size = Footer,
        isEnabled = setLimitButtonEnabled,
        isLoading = setLimitButtonLoading,
        onClick = StandardClick(onSetLimitClick)
      )
  )
}
