package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel

/**
 * Bottom sheet shown when a user attempts to migrate their private wallet but has pending
 * transactions that need to be confirmed first.
 */
data class PrivateWalletMigrationPendingTransactionsWarningSheetModel(
  override val onBack: () -> Unit,
  val onGotIt: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "Update can’t be completed",
      subline = "Your wallet has pending transactions. Once all transactions are confirmed, go to Settings > Private wallet update to complete the update.",
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    primaryButton = ButtonModel(
      text = "Got it",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = SheetClosingClick(onGotIt)
    ),
    renderContext = RenderContext.Sheet,
    id = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_PENDING_TRANSACTIONS_WARNING
  )
