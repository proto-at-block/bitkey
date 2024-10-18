package build.wallet.statemachine.settings.full.feedback

import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.support.SupportTicketData
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.ImmutableList

data class FillingFormBodyModel(
  val formData: StateMapBackedSupportTicketData,
  val onSubmitData: (SupportTicketData) -> Unit,
  val confirmLeaveIfNeeded: () -> Unit,
  val isValid: Boolean,
  override val mainContentList: ImmutableList<FormMainContentModel>,
) : FormBodyModel(
    id = FeedbackEventTrackerScreenId.FEEDBACK_FILLING_FORM,
    onBack = confirmLeaveIfNeeded,
    toolbar =
      ToolbarModel(
        leadingAccessory =
          ToolbarAccessoryModel.IconAccessory.BackAccessory(onClick = confirmLeaveIfNeeded),
        middleAccessory = ToolbarMiddleAccessoryModel(title = "Send feedback")
      ),
    header = null,
    mainContentList = mainContentList,
    primaryButton =
      ButtonModel(
        text = "Submit",
        isEnabled = isValid,
        size = ButtonModel.Size.Footer,
        onClick = StandardClick {
          onSubmitData(formData.toImmutable())
        }
      )
  )
