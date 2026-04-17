package build.wallet.bitcoin.transactions

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.flags.setBdk2Enabled
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.testing.shouldBeOk
import build.wallet.testing.tags.TestTag.IsolatedTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class SpeedUpDepositFunctionalTests : FunSpec({

  /**
   * Constructs a [SpeedUpDepositServiceImpl] wired directly from [AppTester] dependencies,
   * since the service is not yet registered in the DI component.
   */
  fun buildService(app: AppTester) =
    SpeedUpDepositServiceImpl(
      feeRateEstimator = app.bitcoinFeeRateEstimator,
      bitcoinWalletService = app.bitcoinWalletService,
      accountService = app.accountService
    )

  test("prepares, signs, and broadcasts a CPFP child for a pending incoming deposit")
    .config(tags = setOf(IsolatedTest)) {
      val app = launchNewApp()
      app.bdk2FeatureFlag.setBdk2Enabled(true)
      app.onboardFullAccountWithFakeHardware()

      val service = buildService(app)

      // Fund WITHOUT mining so the deposit stays unconfirmed in the mempool
      app.addSomeFunds(amount = sats(50_000), waitForConfirmation = false)

      val spendingWallet = app.getActiveWallet()

      turbineScope(timeout = 30.seconds) {
        spendingWallet.transactions().test {
          spendingWallet.sync().shouldBeOk()

          // Wait for the wallet to observe the unconfirmed incoming tx
          val txs = awaitUntil { txs ->
            txs.any { it.transactionType == Incoming && it.confirmationStatus == Pending }
          }

          val pendingDeposit = txs.first {
            it.transactionType == Incoming && it.confirmationStatus == Pending
          }

          // Prepare the CPFP speed-up transaction
          val speedUp = service.prepareSpeedUpDepositTransaction(pendingDeposit).shouldBeOk()

          // Verify the result structure
          speedUp.parentTxid.shouldBe(pendingDeposit.id)
          speedUp.childFee.amount.isPositive.shouldBeTrue()
          speedUp.transferAmount.isPositive.shouldBeTrue()
          // CPFP should use exactly one input: the parent's unconfirmed output
          speedUp.psbt.numOfInputs.shouldBe(1)

          // Sign with fake hardware and broadcast the child tx
          val hwSigned = app.signPsbtWithHardware(speedUp.psbt)
          app.bitcoinBlockchain.broadcast(hwSigned).shouldBeOk()

          // Mine a block — both parent and child should confirm together
          app.mineBlock()
          spendingWallet.sync().shouldBeOk()

          awaitUntil { updatedTxs ->
            updatedTxs.any {
              it.id == hwSigned.id &&
                it.confirmationStatus is BitcoinTransaction.ConfirmationStatus.Confirmed
            }
          }
        }
      }

      app.returnFundsToTreasury()
    }

  test("re-bumps an already-CPFPd deposit by walking the chain to the child tip")
    .config(tags = setOf(IsolatedTest)) {
      val app = launchNewApp()
      app.bdk2FeatureFlag.setBdk2Enabled(true)
      app.onboardFullAccountWithFakeHardware()

      val service = buildService(app)

      // Large enough to cover two rounds of CPFP fees
      app.addSomeFunds(amount = sats(100_000), waitForConfirmation = false)

      val spendingWallet = app.getActiveWallet()

      turbineScope(timeout = 60.seconds) {
        spendingWallet.transactions().test {
          spendingWallet.sync().shouldBeOk()

          val txs = awaitUntil { txs ->
            txs.any { it.transactionType == Incoming && it.confirmationStatus == Pending }
          }

          val pendingDeposit = txs.first {
            it.transactionType == Incoming && it.confirmationStatus == Pending
          }

          // ── First CPFP ────────────────────────────────────────────────────────────────
          val firstSpeedUp = service.prepareSpeedUpDepositTransaction(pendingDeposit).shouldBeOk()
          val hwSignedChild1 = app.signPsbtWithHardware(firstSpeedUp.psbt)
          app.bitcoinBlockchain.broadcast(hwSignedChild1).shouldBeOk()

          // Sync so the wallet's transactionsData includes the first child (UtxoConsolidation)
          spendingWallet.sync().shouldBeOk()
          awaitUntil { updatedTxs ->
            updatedTxs.any { it.id == hwSignedChild1.id && it.confirmationStatus == Pending }
          }

          // ── Second CPFP (re-bump) ──────────────────────────────────────────────────────
          // Pass the ORIGINAL parent tx — findCpfpChainTip walks: parent → child1 → child1's UTXO
          val secondSpeedUp = service.prepareSpeedUpDepositTransaction(pendingDeposit).shouldBeOk()

          secondSpeedUp.parentTxid.shouldBe(pendingDeposit.id)
          // Second child should not pay less than the first child. In practice this can be equal
          // when both rounds are clamped by the same min-relay floor.
          secondSpeedUp.childFee.amount.fractionalUnitValue.longValue()
            .shouldBeGreaterThanOrEqualTo(firstSpeedUp.childFee.amount.fractionalUnitValue.longValue())

          val hwSignedChild2 = app.signPsbtWithHardware(secondSpeedUp.psbt)
          app.bitcoinBlockchain.broadcast(hwSignedChild2).shouldBeOk()

          // Mine — should confirm the whole parent + child1 + child2 package
          app.mineBlock()
          spendingWallet.sync().shouldBeOk()

          awaitUntil { updatedTxs ->
            updatedTxs.any {
              it.id == hwSignedChild2.id &&
                it.confirmationStatus is BitcoinTransaction.ConfirmationStatus.Confirmed
            }
          }
        }
      }

      app.returnFundsToTreasury()
    }

  test("returns an error when the incoming deposit is already confirmed")
    .config(tags = setOf(IsolatedTest)) {
      val app = launchNewApp()
      app.bdk2FeatureFlag.setBdk2Enabled(true)
      app.onboardFullAccountWithFakeHardware()

      val service = buildService(app)

      // Fund WITH confirmation (default) — a block is mined and the deposit confirms
      app.addSomeFunds(amount = sats(50_000), waitForConfirmation = true)

      val spendingWallet = app.getActiveWallet()

      turbineScope(timeout = 30.seconds) {
        spendingWallet.transactions().test {
          spendingWallet.sync().shouldBeOk()

          val txs = awaitUntil { txs ->
            txs.any {
              it.transactionType == Incoming &&
                it.confirmationStatus is BitcoinTransaction.ConfirmationStatus.Confirmed
            }
          }

          val confirmedDeposit = txs.first {
            it.transactionType == Incoming &&
              it.confirmationStatus is BitcoinTransaction.ConfirmationStatus.Confirmed
          }

          val result = service.prepareSpeedUpDepositTransaction(confirmedDeposit)

          result.isErr.shouldBeTrue()
          result.error.message.shouldBe("CPFP failed: transaction is already confirmed")
          cancelAndIgnoreRemainingEvents()
        }
      }

      app.returnFundsToTreasury()
    }
})
