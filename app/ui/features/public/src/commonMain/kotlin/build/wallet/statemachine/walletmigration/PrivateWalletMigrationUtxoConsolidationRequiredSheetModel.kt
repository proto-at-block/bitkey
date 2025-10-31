package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

/**
 * Bottom sheet shown when a user attempts to migrate their private wallet but has too many
 * UTXOs to sign in a single transaction. Redirects them to UTXO consolidation.
 */
data class PrivateWalletMigrationUtxoConsolidationRequiredSheetModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      headline = "UTXO consolidation required",
      subline = "We've detected a large number of UTXOs in your wallet that need to be consolidated before you can complete the update.",
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = StandardClick(onContinue)
    ),
    renderContext = RenderContext.Sheet,
    id = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_UTXO_CONSOLIDATION_REQUIRED
  )
