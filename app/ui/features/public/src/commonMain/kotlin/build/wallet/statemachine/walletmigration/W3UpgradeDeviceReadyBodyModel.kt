package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.FormScreenTitleModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * "Do you have a new Bitkey device ready?" screen.
 */
data class W3UpgradeDeviceReadyBodyModel(
  override val onBack: (() -> Unit)?,
  val onYes: () -> Unit,
  val onNo: () -> Unit,
  val step: Int = 1,
  val totalSteps: Int = 4,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_DEVICE_READY,
    onBack = onBack,
    toolbar = onBack?.let {
      ToolbarModel(
        leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(
          onClick = onBack
        )
      )
    },
    formScreenTitle = FormScreenTitleModel(
      eyebrow = w3UpgradeStepEyebrow(step, totalSteps),
      title = "Do you have a new Bitkey device ready?"
    ),
    formScreenLayout = FormScreenLayoutModel.LargeTitle(scrollable = false),
    header = FormHeaderModel(
      headline = null,
      sublineModel = StringModel(
        "You'll need to pair a new Bitkey device before you can start the process of replacing the old one."
      )
    ),
    mainContentList = immutableListOf(),
    primaryButton = ButtonModel(
      text = "Yes",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = StandardClick(onYes)
    ),
    secondaryButton = ButtonModel(
      text = "No",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Secondary,
      onClick = StandardClick(onNo)
    )
  )
