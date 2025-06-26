package build.wallet.statemachine.recovery.emergencyexitkit

import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class EmergencyExitKitRestoreWalletBodyModel(
  override val onBack: () -> Unit,
  val onRestore: (() -> Unit)?,
) : FormBodyModel(
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(
        onClick = onBack
      )
    ),
    header = FormHeaderModel(
      headline = "Restore your wallet",
      subline = "Access your wallet on this phone using the Emergency Exit Kit backup of your App Key, with approval from your Bitkey device."
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Restore Bitkey Wallet",
      size = ButtonModel.Size.Footer,
      isEnabled = onRestore != null,
      onClick = onRestore?.let(::StandardClick) ?: StandardClick {}
    ),
    id = EmergencyAccessKitTrackerScreenId.RESTORE_YOUR_WALLET
  )
