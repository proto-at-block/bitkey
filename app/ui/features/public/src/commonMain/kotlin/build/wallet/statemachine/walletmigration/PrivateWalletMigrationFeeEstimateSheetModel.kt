package build.wallet.statemachine.walletmigration

import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint

data class PrivateWalletMigrationFeeEstimateSheetModel(
  override val onBack: () -> Unit,
  val onConfirm: () -> Unit,
  val feeEstimateData: FeeEstimateData,
) : FormBodyModel(
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Confirm wallet update",
      subline = when (feeEstimateData) {
        is FeeEstimateData.InsufficientFunds ->
          "Your balance is less than the current network fees so will not be transferred to your new wallet."
        else ->
          "Review the network fees and have your Bitkey device ready."
      },
      alignment = FormHeaderModel.Alignment.LEADING
    ),
    mainContentList = when (feeEstimateData) {
      is FeeEstimateData.Loading -> immutableListOf(FormMainContentModel.Loader)
      is FeeEstimateData.Loaded -> immutableListOf(
        FormMainContentModel.DataList(
          items = immutableListOf(
            FormMainContentModel.DataList.Data(
              title = "Estimated network fees",
              titleIcon = IconModel(
                icon = Icon.SmallIconInformationFilled,
                iconSize = IconSize.XSmall,
                iconTint = IconTint.On30
              ),
              onTitle = feeEstimateData.onNetworkFeesExplainerClick,
              sideText = feeEstimateData.estimatedFee,
              secondarySideText = feeEstimateData.estimatedFeeSats
            )
          )
        )
      )
      is FeeEstimateData.InsufficientFunds -> immutableListOf()
    },
    primaryButton = when (feeEstimateData) {
      is FeeEstimateData.Loading -> null
      is FeeEstimateData.Loaded,
      is FeeEstimateData.InsufficientFunds,
      -> ButtonModel(
        text = "Confirm",
        size = ButtonModel.Size.Footer,
        treatment = ButtonModel.Treatment.Primary,
        requiresBitkeyInteraction = true,
        onClick = onConfirm
      )
    },
    renderContext = RenderContext.Sheet,
    id = WalletMigrationEventTrackerScreenId.PRIVATE_WALLET_MIGRATION_FEE_ESTIMATE
  )

sealed interface FeeEstimateData {
  /**
   * Currently loading fee estimate.
   */
  data object Loading : FeeEstimateData

  /**
   * Successfully loaded fee estimate.
   */
  data class Loaded(
    val estimatedFee: String,
    val estimatedFeeSats: String,
    val onNetworkFeesExplainerClick: () -> Unit,
  ) : FeeEstimateData

  /**
   * Wallet balance is insufficient to cover network fees.
   */
  data object InsufficientFunds : FeeEstimateData
}
