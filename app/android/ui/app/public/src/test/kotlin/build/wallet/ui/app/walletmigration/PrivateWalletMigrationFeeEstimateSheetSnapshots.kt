package build.wallet.ui.app.walletmigration

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.walletmigration.FeeEstimateData
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationFeeEstimateSheetModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class PrivateWalletMigrationFeeEstimateSheetSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("Private wallet migration fee estimate sheet - loading") {
    paparazzi.snapshotSheet(
      PrivateWalletMigrationFeeEstimateSheetModel(
        onBack = {},
        onConfirm = {},
        feeEstimateData = FeeEstimateData.Loading
      )
    )
  }

  test("Private wallet migration fee estimate sheet - loaded") {
    paparazzi.snapshotSheet(
      PrivateWalletMigrationFeeEstimateSheetModel(
        onBack = {},
        onConfirm = {},
        feeEstimateData = FeeEstimateData.Loaded(
          estimatedFee = "$1.00",
          estimatedFeeSats = "885 sats",
          onNetworkFeesExplainerClick = {}
        )
      )
    )
  }

  test("Private wallet migration fee estimate sheet - insufficient funds") {
    paparazzi.snapshotSheet(
      PrivateWalletMigrationFeeEstimateSheetModel(
        onBack = {},
        onConfirm = {},
        feeEstimateData = FeeEstimateData.InsufficientFunds
      )
    )
  }
})
