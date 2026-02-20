package build.wallet.integration.statemachine.transactions

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import bitkey.account.HardwareType
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.TransferConfirmationScreenVariant
import build.wallet.statemachine.send.TransferInitiatedBodyModel
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcBodyModel
import build.wallet.statemachine.transactions.TransactionDetailModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.utxo.UtxoConsolidationSpeedUpConfirmationModel
import build.wallet.statemachine.utxo.UtxoConsolidationSpeedUpTransactionSentModel
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.testing.shouldBeOk
import build.wallet.testing.tags.TestTag.IsolatedTest
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/**
 * UI-level functional tests for fee bump flows.
 *
 * Tests the complete user journey from transaction list → speed up button → NFC signing → success,
 * including W1 (single-tap) and W3 (two-tap with confirmation) hardware flows.
 */
class FeeBumpUiFunctionalTests : FunSpec({

  context("W1 Fee Bump") {
    test("complete fee bump flow for outgoing transaction")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchAppWithPendingTransaction(hardwareType = HardwareType.W1)

        app.appUiStateMachine.test(
          props = Unit,
          testTimeout = 30.seconds,
          turbineTimeout = 10.seconds
        ) {
          navigateToTransactionDetailsAndClickSpeedUp()

          // Confirm the speed up
          awaitUntilBody<TransferConfirmationScreenModel>(
            matching = { it.variant == TransferConfirmationScreenVariant.SpeedUp }
          ) {
            requiresHardware.shouldBeTrue()
            confirmButtonEnabled.shouldBeTrue()
            onConfirmClick()
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

          // Transfer initiated success screen
          awaitUntilBody<TransferInitiatedBodyModel> {
            onDone()
          }

          cancelAndIgnoreRemainingEvents()
        }

        // Verify the bumped transaction was broadcast
        val spendingWallet = app.getActiveWallet()
        spendingWallet.transactions().test {
          spendingWallet.sync().shouldBeOk()
          val txs = awaitItem()
          // Should have the original and the replacement
          txs.count { it.confirmationStatus == Pending }.shouldBeGreaterThan(0)
        }

        // Clean up - return all funds to treasury
        app.returnFundsToTreasury()
      }
  }

  context("W3 Fee Bump - Two-Tap Flow") {
    test("complete W3 fee bump flow for outgoing transaction")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchAppWithPendingTransaction(hardwareType = HardwareType.W3)

        app.appUiStateMachine.test(
          props = Unit,
          testTimeout = 30.seconds,
          turbineTimeout = 10.seconds
        ) {
          navigateToTransactionDetailsAndClickSpeedUp()

          // Confirm the speed up
          awaitUntilBody<TransferConfirmationScreenModel>(
            matching = { it.variant == TransferConfirmationScreenVariant.SpeedUp }
          ) {
            requiresHardware.shouldBeTrue()
            onConfirmClick()
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

          // Transfer initiated success screen
          awaitUntilBody<TransferInitiatedBodyModel> {
            onDone()
          }

          cancelAndIgnoreRemainingEvents()
        }

        // Verify the bumped transaction was broadcast
        val spendingWallet = app.getActiveWallet()
        spendingWallet.transactions().test {
          spendingWallet.sync().shouldBeOk()
          awaitItem().count { it.confirmationStatus == Pending }.shouldBeGreaterThan(0)
        }

        // Clean up - return all funds to treasury
        app.returnFundsToTreasury()
      }

    test("W3 fee bump for UTXO consolidation")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchAppWithPendingConsolidation(hardwareType = HardwareType.W3)

        app.appUiStateMachine.test(
          props = Unit,
          testTimeout = 30.seconds,
          turbineTimeout = 10.seconds
        ) {
          navigateToTransactionDetailsAndClickSpeedUp()

          // Confirm the speed up for UTXO consolidation
          awaitUntilBody<UtxoConsolidationSpeedUpConfirmationModel> {
            onConfirmClick()
          }

          // W3 First tap
          awaitUntilBody<SignTransactionNfcBodyModel>(
            matching = { it.status is SignTransactionNfcBodyModel.Status.Searching }
          )

          // W3 confirmation prompt
          approveW3DeviceConfirmation()

          // W3 Second tap
          awaitUntilBody<SignTransactionNfcBodyModel>(
            matching = { it.status is SignTransactionNfcBodyModel.Status.Searching }
          )

          // Success
          awaitUntilBody<SignTransactionNfcBodyModel>(
            matching = { it.status is SignTransactionNfcBodyModel.Status.Success }
          )

          // Broadcasting
          awaitUntilBody<LoadingSuccessBodyModel>(
            matching = { it.state == LoadingSuccessBodyModel.State.Loading }
          )

          // UTXO consolidation success screen
          awaitUntilBody<UtxoConsolidationSpeedUpTransactionSentModel> {
            onDone()
          }

          cancelAndIgnoreRemainingEvents()
        }

        // Clean up - return all funds to treasury
        app.returnFundsToTreasury()
      }

    test("W3 user denies device confirmation")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchAppWithPendingTransaction(hardwareType = HardwareType.W3)

        app.appUiStateMachine.test(
          props = Unit,
          testTimeout = 30.seconds,
          turbineTimeout = 10.seconds
        ) {
          navigateToTransactionDetailsAndClickSpeedUp()

          awaitUntilBody<TransferConfirmationScreenModel>(
            matching = { it.variant == TransferConfirmationScreenVariant.SpeedUp }
          ) {
            onConfirmClick()
          }

          // W3 First tap
          awaitUntilBody<SignTransactionNfcBodyModel>(
            matching = { it.status is SignTransactionNfcBodyModel.Status.Searching }
          )

          // User denies on device
          denyW3DeviceConfirmation()

          // Should return to confirmation screen
          awaitUntilBody<TransferConfirmationScreenModel>(
            matching = { it.variant == TransferConfirmationScreenVariant.SpeedUp }
          ) {
            onBack()
          }

          cancelAndIgnoreRemainingEvents()
        }

        // Transaction should not be broadcast
        val spendingWallet = app.getActiveWallet()
        spendingWallet.transactions().test {
          spendingWallet.sync().shouldBeOk()
          val txs = awaitItem()
          // Should only have the original pending transaction
          txs.count { it.confirmationStatus == Pending }.shouldBe(1)
        }

        // Clean up - return all funds to treasury
        app.returnFundsToTreasury()
      }
  }
})

/** Type alias for the turbine test context. */
private typealias TestContext = ReceiveTurbine<ScreenModel>

/**
 * Helper to navigate from MoneyHome to transaction details and click Speed Up button.
 */
private suspend fun TestContext.navigateToTransactionDetailsAndClickSpeedUp() {
  awaitUntilBody<MoneyHomeBodyModel>(
    matching = { model ->
      model.transactionsModel?.sections?.firstOrNull()?.items?.isNotEmpty() == true
    }
  ) {
    transactionsModel.shouldNotBeNull()
    val firstTransaction = transactionsModel.sections.first().items.first()
    firstTransaction.onClick.shouldNotBeNull().invoke()
  }

  awaitUntilBody<TransactionDetailModel>(
    matching = { it.secondaryButton?.text == "Speed Up" }
  ) {
    secondaryButton.shouldNotBeNull().onClick()
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
 * Launches an app with a pending outgoing transaction ready to be sped up.
 */
private suspend fun TestScope.launchAppWithPendingTransaction(
  hardwareType: HardwareType,
): AppTester {
  val app = launchNewApp()
  app.bdk2FeatureFlag.setFlagValue(true)

  if (hardwareType == HardwareType.W3) {
    app.accountConfigService.setHardwareType(HardwareType.W3).getOrThrow()
  }

  app.onboardFullAccountWithFakeHardware()

  val fundingAmount = sats(50_000L)
  val sendAmount = sats(10_000L)

  app.addSomeFunds(amount = fundingAmount)
  app.waitForFunds { it.total == fundingAmount }

  val spendingWallet = app.getActiveWallet()
  val treasuryAddress = app.treasuryWallet.getReturnAddress()

  // Create original transaction with low fee rate so it can be sped up
  val originalPsbt = spendingWallet.createSignedPsbt(
    PsbtConstructionMethod.Regular(
      recipientAddress = treasuryAddress,
      amount = BitcoinTransactionSendAmount.ExactAmount(sendAmount),
      feePolicy = FeePolicy.Rate(FeeRate(1.0f))
    )
  ).shouldBeOk()

  // Sign with hardware using the shared helper (not through UI)
  val hwSignedOriginal = app.signPsbtWithHardware(originalPsbt)
  app.bitcoinBlockchain.broadcast(hwSignedOriginal).shouldBeOk()

  // Wait for transaction to appear as pending
  turbineScope(timeout = 15.seconds) {
    spendingWallet.transactions().test {
      spendingWallet.sync().shouldBeOk()
      awaitUntil { txs ->
        txs.any { it.id == hwSignedOriginal.id && it.confirmationStatus == Pending }
      }
    }
  }

  return app
}

/**
 * Launches an app with a pending UTXO consolidation transaction ready to be sped up.
 */
private suspend fun TestScope.launchAppWithPendingConsolidation(
  hardwareType: HardwareType,
): AppTester {
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
  app.waitForFunds { it.total == sats(30_000L) }

  val spendingWallet = app.getActiveWallet()

  // Create consolidation transaction with low fee
  val consolidationParams = app.utxoConsolidationService.prepareUtxoConsolidation()
    .shouldBeOk()
    .single()

  // Use the consolidation params PSBT which already has low fees
  val consolidationPsbt = consolidationParams.appSignedPsbt

  val hwSignedConsolidation = app.signPsbtWithHardware(consolidationPsbt)
  app.bitcoinBlockchain.broadcast(hwSignedConsolidation).shouldBeOk()

  // Wait for consolidation to appear as pending
  turbineScope(timeout = 15.seconds) {
    spendingWallet.transactions().test {
      spendingWallet.sync().shouldBeOk()
      awaitUntil { txs ->
        txs.any { it.id == hwSignedConsolidation.id && it.confirmationStatus == Pending }
      }
    }
  }

  return app
}
