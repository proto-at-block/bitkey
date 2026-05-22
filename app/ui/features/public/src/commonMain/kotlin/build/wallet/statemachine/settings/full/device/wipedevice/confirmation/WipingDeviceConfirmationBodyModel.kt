package build.wallet.statemachine.settings.full.device.wipedevice.confirmation

import build.wallet.compose.collections.buildImmutableList
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceEventTrackerScreenId
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class WipingDeviceConfirmationBodyModel(
  override val onBack: () -> Unit,
  val onConfirmWipeDevice: () -> Unit,
  val messageItemModels: ImmutableList<WipingDeviceConfirmationItemModel>,
  val isConfirmEnabled: Boolean,
) : FormBodyModel(
    id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_CONFIRMATION,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(onBack)
    ),
    header = FormHeaderModel(
      headline = "Before you continue...",
      subline = "Please read and confirm the following:"
    ),
    mainContentList = confirmationItemsToMainContentList(messageItemModels),
    primaryButton = ButtonModel(
      text = "Wipe device",
      isEnabled = isConfirmEnabled,
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = StandardClick(onConfirmWipeDevice)
    )
  )

private fun confirmationItemsToMainContentList(
  messageItemModels: ImmutableList<WipingDeviceConfirmationItemModel>,
): ImmutableList<FormMainContentModel> =
  buildImmutableList {
    add(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          items = messageItemModels.map { itemModel ->
            itemModel.toListItem()
          }.toImmutableList(),
          style = ListGroupStyle.DIVIDER
        )
      )
    )
  }

private fun WipingDeviceConfirmationItemModel.toListItem() =
  ListItemModel(
    leadingAccessory = state.leadingAccessory(
      onClick = onClick
    ),
    title = title,
    treatment = ListItemTreatment.PRIMARY
  )

private fun WipingDeviceConfirmationState.leadingAccessory(
  onClick: () -> Unit,
): ListItemAccessory =
  ListItemAccessory.CheckboxAccessory(
    isChecked = this is WipingDeviceConfirmationState.Completed,
    onClick = onClick
  )
