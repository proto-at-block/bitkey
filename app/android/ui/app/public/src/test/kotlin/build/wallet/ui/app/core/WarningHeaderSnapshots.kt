package build.wallet.ui.app.core

import build.wallet.analytics.events.screen.id.InactiveWalletSweepEventTrackerScreenId
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.recovery.sweep.multipleTransactionsWarningScreenModel
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationPendingTransactionsWarningSheetModel
import build.wallet.statemachine.walletmigration.W3UpgradePendingTransactionsWarningSheetModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class WarningHeaderSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("private wallet migration pending transactions warning") {
    paparazzi.snapshotSheet(
      model = PrivateWalletMigrationPendingTransactionsWarningSheetModel(onBack = {}, onGotIt = {})
    )
  }

  test("w3 upgrade pending transactions warning") {
    paparazzi.snapshotSheet(
      model = W3UpgradePendingTransactionsWarningSheetModel(onBack = {}, onGotIt = {})
    )
  }

  test("multiple transactions warning") {
    paparazzi.snapshot {
      FormScreen(
        model =
          multipleTransactionsWarningScreenModel(
            id = InactiveWalletSweepEventTrackerScreenId.INACTIVE_WALLET_SWEEP_MULTIPLE_TRANSACTIONS_WARNING,
            transactionCount = 3,
            onContinue = {},
            onBack = {},
            presentationStyle = ScreenPresentationStyle.Root
          ).body as FormBodyModel
      )
    }
  }
})
