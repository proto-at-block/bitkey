package build.wallet.integration.statemachine.utxo

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import bitkey.account.HardwareType
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.UtxoConsolidation
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.utxo.TapAndHoldToConsolidateUtxosBodyModel
import build.wallet.statemachine.utxo.UtxoConsolidationConfirmationModel
import build.wallet.statemachine.utxo.UtxoConsolidationTransactionSentModel
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.testing.shouldBeOk
import build.wallet.testing.tags.TestTag.IsolatedTest
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/**
 * UI-level functional tests for UTXO consolidation flows.
 *
 * Tests the complete user journey from MoneyHome → UTXO consolidation → NFC signing → success,
 * including W1 (single-tap) and W3 (two-tap with confirmation) hardware flows.
 */
class UtxoConsolidationUiFunctionalTests : FunSpec({

  context("W1 UTXO Consolidation") {
    test("complete consolidation flow with single tap")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchAppWithUtxos(hardwareType = HardwareType.W1)

        app.appUiStateMachine.test(
          props = Unit,
          testTimeout = 30.seconds,
          turbineTimeout = 10.seconds
        ) {
          navigateToUtxoConsolidation()

          // Loading state
          awaitUntilBody<LoadingSuccessBodyModel>(
            matching = { it.state == LoadingSuccessBodyModel.State.Loading }
          )

          // Confirmation screen
          awaitUntilBody<UtxoConsolidationConfirmationModel> {
            balanceTitle.shouldBe("Wallet balance")
            onContinue()
          }

          // Tap & Hold sheet
          awaitUntilBody<TapAndHoldToConsolidateUtxosBodyModel> {
            onConsolidate()
          }

          // W1 NFC signing - single tap
          awaitUntilBody<SignTransactionNfcBodyModel>(
            matching = { it.status is SignTransactionNfcBodyModel.Status.Searching }
          )

          // Wait for success
          awaitUntilBody<SignTransactionNfcBodyModel>(
            matching = { it.status is SignTransactionNfcBodyModel.Status.Success }
          )

          // Broadcasting
          awaitUntilBody<LoadingSuccessBodyModel>(
            matching = { it.state == LoadingSuccessBodyModel.State.Loading }
          )

          // Success screen
          awaitUntilBody<UtxoConsolidationTransactionSentModel> {
            utxosCountConsolidated.shouldBe("3 → 1")
            onDone()
          }

          cancelAndIgnoreRemainingEvents()
        }

        // Verify consolidation was broadcast
        val spendingWallet = app.getActiveWallet()
        spendingWallet.transactions().test {
          spendingWallet.sync().shouldBeOk()
          // Wait deterministically for the consolidation transaction to appear
          val txs = awaitUntil { it.any { tx -> tx.transactionType == UtxoConsolidation } }
          // Should have a UTXO consolidation transaction
          val consolidationTx = txs.firstOrNull { it.transactionType == UtxoConsolidation }
          consolidationTx.shouldNotBeNull()
        }

        // Clean up - return all funds to treasury
        app.returnFundsToTreasury()
      }
  }

  context("W3 UTXO Consolidation - Two-Tap Flow") {
    test("complete W3 consolidation flow with two taps and device confirmation")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchAppWithUtxos(hardwareType = HardwareType.W3)

        app.appUiStateMachine.test(
          props = Unit,
          testTimeout = 30.seconds,
          turbineTimeout = 10.seconds
        ) {
          navigateToUtxoConsolidation()

          // Loading state
          awaitUntilBody<LoadingSuccessBodyModel>(
            matching = { it.state == LoadingSuccessBodyModel.State.Loading }
          )

          // Confirmation screen
          awaitUntilBody<UtxoConsolidationConfirmationModel> {
            balanceTitle.shouldBe("Wallet balance")
            onContinue()
          }

          // Tap & Hold sheet
          awaitUntilBody<TapAndHoldToConsolidateUtxosBodyModel> {
            onConsolidate()
          }

          // W3 First tap - searching for NFC
          awaitUntilBody<SignTransactionNfcBodyModel>(
            matching = { it.status is SignTransactionNfcBodyModel.Status.Searching }
          )

          // W3 shows emulated confirmation prompt after chunked transfer
          approveW3DeviceConfirmation()

          // W3 Second tap - searching for NFC again
          awaitUntilBody<SignTransactionNfcBodyModel>(
            matching = { it.status is SignTransactionNfcBodyModel.Status.Searching }
          )

          // Wait for success
          awaitUntilBody<SignTransactionNfcBodyModel>(
            matching = { it.status is SignTransactionNfcBodyModel.Status.Success }
          )

          // Broadcasting
          awaitUntilBody<LoadingSuccessBodyModel>(
            matching = { it.state == LoadingSuccessBodyModel.State.Loading }
          )

          // Success screen
          awaitUntilBody<UtxoConsolidationTransactionSentModel> {
            utxosCountConsolidated.shouldBe("3 → 1")
            onDone()
          }

          cancelAndIgnoreRemainingEvents()
        }

        // Verify consolidation was broadcast
        val spendingWallet = app.getActiveWallet()
        spendingWallet.transactions().test {
          spendingWallet.sync().shouldBeOk()
          // Wait deterministically for the consolidation transaction to appear
          val txs = awaitUntil { it.any { tx -> tx.transactionType == UtxoConsolidation } }
          // Should have a UTXO consolidation transaction
          val consolidationTx = txs.firstOrNull { it.transactionType == UtxoConsolidation }
          consolidationTx.shouldNotBeNull()
        }

        // Clean up - return all funds to treasury
        app.returnFundsToTreasury()
      }

    test("W3 user denies device confirmation")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchAppWithUtxos(hardwareType = HardwareType.W3)

        app.appUiStateMachine.test(
          props = Unit,
          testTimeout = 30.seconds,
          turbineTimeout = 10.seconds
        ) {
          navigateToUtxoConsolidation()

          // Loading state
          awaitUntilBody<LoadingSuccessBodyModel>(
            matching = { it.state == LoadingSuccessBodyModel.State.Loading }
          )

          // Confirmation screen
          awaitUntilBody<UtxoConsolidationConfirmationModel> {
            onContinue()
          }

          // Tap & Hold sheet
          awaitUntilBody<TapAndHoldToConsolidateUtxosBodyModel> {
            onConsolidate()
          }

          // W3 First tap
          awaitUntilBody<SignTransactionNfcBodyModel>(
            matching = { it.status is SignTransactionNfcBodyModel.Status.Searching }
          )

          // User denies on device
          denyW3DeviceConfirmation()

          // Should return to confirmation screen
          awaitUntilBody<UtxoConsolidationConfirmationModel> {
            balanceTitle.shouldBe("Wallet balance")
            onBack()
          }

          cancelAndIgnoreRemainingEvents()
        }

        // Verify no consolidation was broadcast
        val spendingWallet = app.getActiveWallet()
        spendingWallet.transactions().test(timeout = 5.seconds) {
          spendingWallet.sync().shouldBeOk()

          // Get the post-sync emission and verify no consolidation tx
          // Note: sync() completes before we await, so this should be post-sync data
          val txs = awaitItem()
          txs.count { it.transactionType == UtxoConsolidation }.shouldBe(0)

          // Wait for the timeout period to ensure no subsequent emissions contain a consolidation tx
          // This catches cases where a consolidation tx might appear in a delayed emission
          expectNoEvents()
        }

        // Clean up - return all funds to treasury
        app.returnFundsToTreasury()
      }
  }
})

/** Type alias for the turbine test context. */
private typealias TestContext = ReceiveTurbine<ScreenModel>

/**
 * Helper to navigate from MoneyHome to UTXO consolidation screen.
 */
private suspend fun TestContext.navigateToUtxoConsolidation() {
  // Wait for MoneyHome to be ready and navigate to settings
  awaitUntilBody<MoneyHomeBodyModel> {
    onSettings()
  }

  // Navigate through settings to UTXO consolidation
  // Click on the UTXO consolidation option in settings
  awaitUntilBody<SettingsBodyModel>(
    matching = {
      it.sectionModels.any { section ->
        section.rowModels.any { row -> row.title == "Consolidate UTXOs" }
      }
    }
  ) {
    val consolidationRow = sectionModels
      .flatMap { it.rowModels }
      .first { it.title == "Consolidate UTXOs" }
    consolidationRow.onClick()
  }
}

/**
 * Approves the W3 device confirmation prompt.
 */
private suspend fun TestContext.approveW3DeviceConfirmation() {
  awaitUntilBody<PromptSelectionFormBodyModel> {
    options.shouldBe(listOf("Approve", "Deny"))
    onOptionSelected(0)
  }
}

/**
 * Denies the W3 device confirmation prompt.
 */
private suspend fun TestContext.denyW3DeviceConfirmation() {
  awaitUntilBody<PromptSelectionFormBodyModel> {
    options.shouldBe(listOf("Approve", "Deny"))
    onOptionSelected(1)
  }
}

/**
 * Launches an app with multiple UTXOs ready for consolidation.
 */
private suspend fun TestScope.launchAppWithUtxos(hardwareType: HardwareType): AppTester {
  val app = launchNewApp()
  app.bdk2FeatureFlag.setFlagValue(true)

  if (hardwareType == HardwareType.W3) {
    app.accountConfigService.setHardwareType(HardwareType.W3).getOrThrow()
  }

  app.onboardFullAccountWithFakeHardware()

  // Add multiple small UTXOs to create consolidation opportunity
  app.addSomeFunds(amount = sats(10_000L))
  app.addSomeFunds(amount = sats(10_000L))
  app.addSomeFunds(amount = sats(10_000L))

  // Wait for all UTXOs to be confirmed
  app.waitForFunds { it.total == sats(30_000L) }

  return app
}
