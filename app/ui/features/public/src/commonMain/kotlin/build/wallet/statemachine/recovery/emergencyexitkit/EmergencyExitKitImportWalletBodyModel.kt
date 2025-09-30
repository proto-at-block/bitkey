package build.wallet.statemachine.recovery.emergencyexitkit

import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class EmergencyExitKitImportWalletBodyModel(
  override val onBack: () -> Unit,
  val onScanQRCode: () -> Unit,
  val onEnterManually: () -> Unit,
) : FormBodyModel(
    id = EmergencyAccessKitTrackerScreenId.SELECT_IMPORT_METHOD,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.BackAccessory(
        onClick = onBack
      )
    ),
    header = FormHeaderModel(
      headline = "Use your Emergency Exit Kit",
      subline = "Your Emergency Exit Kit is a PDF document that allows you to exit Bitkey in extreme cases."
    ),
    mainContentList = immutableListOf(
      FormMainContentModel.Explainer(
        items = immutableListOf(
          FormMainContentModel.Explainer.Statement(
            leadingIcon = Icon.SmallIconCloud,
            title = "Find your Emergency Exit Kit",
            body = "Navigate to your device's cloud file manager to locate and download your Emergency Exit Kit."
          ),
          FormMainContentModel.Explainer.Statement(
            leadingIcon = Icon.SmallIconQrCode,
            title = "Scan or enter manually",
            body = "Scan the QR code for easy access to your Bitkey backup or enter the details manually."
          )
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Scan QR code",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onScanQRCode)
    ),
    secondaryButton = ButtonModel(
      text = "Enter details manually",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onEnterManually)
    )
  )
