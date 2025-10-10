package build.wallet.statemachine.recovery.cloud

import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

data class WarningAboutDeletingBackupBodyModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = null,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(headline = "Delete your current backup and create a new account"),
    mainContentList = immutableListOf(
      FormMainContentModel.Explainer(
        items = immutableListOf(
          FormMainContentModel.Explainer.Statement(
            title = null,
            body = "Always transfer the funds from this wallet to a new wallet before deleting this backup."
          ),
          FormMainContentModel.Explainer.Statement(
            title = null,
            body = "Deleting a backup may lead to permanent loss of funds."
          )
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      onClick = StandardClick { onContinue() },
      size = ButtonModel.Size.Footer
    )
  )
