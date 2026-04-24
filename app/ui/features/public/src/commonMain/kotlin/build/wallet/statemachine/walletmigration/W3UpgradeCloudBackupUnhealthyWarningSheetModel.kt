package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel

/**
 * Bottom sheet shown when a user attempts the W3 upgrade but their cloud
 * backup is not healthy. The upgrade requires a healthy backup so that the
 * user can safely recover if anything goes wrong during the migration.
 */
data class W3UpgradeCloudBackupUnhealthyWarningSheetModel(
  override val onBack: () -> Unit,
  val onRepair: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "Upgrade can't be started",
      subline = "Your cloud backup needs to be healthy before you can upgrade. " +
        "Please repair your cloud backup and then return to complete the upgrade.",
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    primaryButton = ButtonModel(
      text = "Repair cloud backup",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = SheetClosingClick(onRepair)
    ),
    renderContext = RenderContext.Sheet,
    id = WalletMigrationEventTrackerScreenId.W3_UPGRADE_CLOUD_BACKUP_UNHEALTHY_WARNING
  )
