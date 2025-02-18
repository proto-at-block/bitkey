package build.wallet.statemachine.trustedcontact.model

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class AskingIfHasHardwareBodyModel(
  override val onBack: () -> Unit,
  val onYes: () -> Unit,
  val isYesChecked: Boolean,
  val onNo: () -> Unit,
  val isNoChecked: Boolean,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ASKING_IF_HAS_HARDWARE,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory {
        onBack()
      }
    ),
    header = FormHeaderModel(
      headline = "Do you have a Bitkey?",
      subline = "You will need a Bitkey to get set up."
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        ListGroupModel(
          items = immutableListOf(
            ListItemModel(
              title = "Yes, I have a Bitkey",
              trailingAccessory = build.wallet.ui.model.list.ListItemAccessory.CheckAccessory(isYesChecked),
              onClick = onYes
            ),
            ListItemModel(
              title = "No, I don't have a Bitkey",
              trailingAccessory = build.wallet.ui.model.list.ListItemAccessory.CheckAccessory(isNoChecked),
              onClick = onNo
            )
          ),
          style = ListGroupStyle.CARD_ITEM
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      treatment = ButtonModel.Treatment.Primary,
      isEnabled = isYesChecked || isNoChecked,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick {
        onContinue()
      }
    ),
    onBack = onBack
  )
