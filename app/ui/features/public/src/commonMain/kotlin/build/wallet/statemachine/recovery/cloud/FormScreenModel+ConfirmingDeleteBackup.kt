package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class ConfirmingDeleteBackupBodyModel(
  val firstOptionIsConfirmed: Boolean,
  val secondOptionIsConfirmed: Boolean,
  val onClickFirstOption: () -> Unit,
  val onClickSecondOption: () -> Unit,
  override val onBack: () -> Unit,
  val onConfirmDelete: () -> Unit,
) : FormBodyModel(
    id = CloudEventTrackerScreenId.CONFIRM_OVERWRITE_FULL_ACCOUNT_CLOUD_BACKUP_DURING_LITE_ACCOUNT_ONBOARDING,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      headline = "Confirm to continue",
      subline = "I understand that:",
      iconModel = null,
      sublineTreatment = SublineTreatment.SMALL
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          items = immutableListOf(
            ListItemModel(
              leadingAccessory = ListItemAccessory.IconAccessory(
                iconPadding = 12,
                model = IconModel(
                  icon = if (firstOptionIsConfirmed) Icon.SmallIconCheckboxSelected else Icon.SmallIconCheckbox,
                  iconSize = IconSize.Small
                )
              ),
              title = "I no longer intend to use this backup to recover my wallet.",
              onClick = onClickFirstOption
            ),
            ListItemModel(
              leadingAccessory = ListItemAccessory.IconAccessory(
                iconPadding = 12,
                model = IconModel(
                  icon = if (secondOptionIsConfirmed) Icon.SmallIconCheckboxSelected else Icon.SmallIconCheckbox,
                  iconSize = IconSize.Small
                )
              ),
              title = "I no longer intend to have access to this account via this backup.",
              onClick = onClickSecondOption
            )
          ),
          style = ListGroupStyle.DIVIDER
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Delete backup",
      treatment = ButtonModel.Treatment.PrimaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick { onConfirmDelete() },
      isEnabled = firstOptionIsConfirmed && secondOptionIsConfirmed
    )
  )
