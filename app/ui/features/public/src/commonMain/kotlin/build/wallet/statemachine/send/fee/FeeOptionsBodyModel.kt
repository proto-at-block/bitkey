package build.wallet.statemachine.send.fee

import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon.LargeIconSpeedometer
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class FeeOptionsBodyModel(
  val title: String,
  val feeOptions: FeeOptionList,
  override val primaryButton: ButtonModel,
  override val onBack: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      icon = LargeIconSpeedometer,
      headline = title,
      alignment = CENTER
    ),
    mainContentList = immutableListOf(feeOptions),
    primaryButton = primaryButton,
    id = SendEventTrackerScreenId.SEND_SELECT_TRANSACTION_FEE_PRIORITY,
    eventTrackerShouldTrack = false
  )
