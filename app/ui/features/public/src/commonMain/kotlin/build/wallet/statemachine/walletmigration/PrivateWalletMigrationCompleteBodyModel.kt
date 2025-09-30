package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class PrivateWalletMigrationCompleteBodyModel(
  override val onBack: () -> Unit,
  val onComplete: () -> Unit,
) : FormBodyModel(
    id = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_COMPLETE,
    onBack = onBack,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(
        onClick = onBack
      )
    ),
    header = FormHeaderModel(
      iconModel = IconModel(
        icon = Icon.LargeIconCheckFilled,
        iconSize = IconSize.Large
      ),
      headline = "Your wallet upgrade is complete",
      subline = "You're ready to start using your enhanced privacy wallet now.",
      sublineTreatment = FormHeaderModel.SublineTreatment.REGULAR
    ),
    primaryButton = ButtonModel(
      text = "Got it",
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onComplete)
    )
  )
