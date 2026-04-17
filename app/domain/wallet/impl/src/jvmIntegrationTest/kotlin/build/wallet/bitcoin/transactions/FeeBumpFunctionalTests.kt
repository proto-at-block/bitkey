package build.wallet.bitcoin.transactions

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.flags.setBdk2Enabled
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.getActiveWallet
import build.wallet.testing.ext.mineBlock
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.returnFundsToTreasury
import build.wallet.testing.ext.signPsbtWithHardware
import build.wallet.testing.ext.waitForFunds
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.testing.tags.TestTag.IsolatedTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class FeeBumpFunctionalTests : FunSpec({

  context("FeeBump - transactions with change") {
    test("creates valid replacement PSBT with higher fee")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val fundingAmount = sats(50_000L)
        val sendAmount = sats(10_000L)

        app.addSomeFunds(amount = fundingAmount)
        app.waitForFunds { it.total == fundingAmount }

        val spendingWallet = app.getActiveWallet()
        val treasuryAddress = app.treasuryWallet.getReturnAddress()

        // Create original transaction with low fee rate
        val originalPsbt = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.Regular(
            recipientAddress = treasuryAddress,
            amount = BitcoinTransactionSendAmount.ExactAmount(sendAmount),
            feePolicy = FeePolicy.Rate(FeeRate(1.0f))
          )
        ).shouldBeOk()

        val hwSignedOriginal = app.signPsbtWithHardware(originalPsbt)
        app.bitcoinBlockchain.broadcast(hwSignedOriginal).shouldBeOk()

        turbineScope(timeout = 30.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            val txs = awaitUntil { txs ->
              txs.any { it.id == hwSignedOriginal.id && it.confirmationStatus == Pending }
            }

            val pendingTx = txs.single { it.id == hwSignedOriginal.id }
            val originalFee = pendingTx.fee.shouldNotBeNull()

            // Create bump fee transaction with higher fee rate
            val bumpFeePsbt = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.FeeBump(
                txid = pendingTx.id,
                feeRate = FeeRate(5.0f)
              )
            ).shouldBeOk()

            // Verify the bump fee PSBT has higher fee and RBF signaling
            bumpFeePsbt.should {
              it.fee.amount.fractionalUnitValue.longValue().shouldBeGreaterThan(originalFee.fractionalUnitValue.longValue())
              it.numOfInputs.shouldBeGreaterThan(0)
              // Verify RBF signaling is preserved
              it.inputs.any { input -> input.sequence < 0xFFFFFFFEu }.shouldBeTrue()
            }

            // Sign and broadcast the replacement
            val hwSignedReplacement = app.signPsbtWithHardware(bumpFeePsbt)
            app.bitcoinBlockchain.broadcast(hwSignedReplacement).shouldBeOk()

            spendingWallet.sync().shouldBeOk()

            awaitUntil { updatedTxs ->
              updatedTxs.any { it.id == hwSignedReplacement.id && it.confirmationStatus == Pending }
            }
          }
        }

        app.returnFundsToTreasury()
      }

    test("replacement replaces original transaction in mempool")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val fundingAmount = sats(50_000L)
        val sendAmount = sats(10_000L)

        app.addSomeFunds(amount = fundingAmount)
        app.waitForFunds { it.total == fundingAmount }

        val spendingWallet = app.getActiveWallet()
        val treasuryAddress = app.treasuryWallet.getReturnAddress()

        val originalPsbt = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.Regular(
            recipientAddress = treasuryAddress,
            amount = BitcoinTransactionSendAmount.ExactAmount(sendAmount),
            feePolicy = FeePolicy.Rate(FeeRate(1.0f))
          )
        ).shouldBeOk()

        val hwSignedOriginal = app.signPsbtWithHardware(originalPsbt)
        app.bitcoinBlockchain.broadcast(hwSignedOriginal).shouldBeOk()

        turbineScope(timeout = 30.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            awaitUntil { txs ->
              txs.any { it.id == hwSignedOriginal.id && it.confirmationStatus == Pending }
            }

            val bumpFeePsbt = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.FeeBump(
                txid = hwSignedOriginal.id,
                feeRate = FeeRate(10.0f)
              )
            ).shouldBeOk()

            val hwSignedReplacement = app.signPsbtWithHardware(bumpFeePsbt)
            app.bitcoinBlockchain.broadcast(hwSignedReplacement).shouldBeOk()

            // Mine a block to confirm the replacement
            app.mineBlock()

            spendingWallet.sync().shouldBeOk()

            val finalTxs = awaitUntil { txs ->
              txs.any {
                it.id == hwSignedReplacement.id &&
                  it.confirmationStatus is BitcoinTransaction.ConfirmationStatus.Confirmed
              }
            }

            // Replacement should be confirmed
            val confirmedReplacement = finalTxs.single { it.id == hwSignedReplacement.id }
            (confirmedReplacement.confirmationStatus is BitcoinTransaction.ConfirmationStatus.Confirmed)
              .shouldBeTrue()

            // Original should no longer be in the transaction list (replaced)
            finalTxs.none { it.id == hwSignedOriginal.id }.shouldBeTrue()
          }
        }

        app.returnFundsToTreasury()
      }

    test("supports multiple consecutive bumps")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val fundingAmount = sats(100_000L)
        val sendAmount = sats(10_000L)

        app.addSomeFunds(amount = fundingAmount)
        app.waitForFunds { it.total == fundingAmount }

        val spendingWallet = app.getActiveWallet()
        val treasuryAddress = app.treasuryWallet.getReturnAddress()

        val originalPsbt = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.Regular(
            recipientAddress = treasuryAddress,
            amount = BitcoinTransactionSendAmount.ExactAmount(sendAmount),
            feePolicy = FeePolicy.Rate(FeeRate(1.0f))
          )
        ).shouldBeOk()

        val hwSignedOriginal = app.signPsbtWithHardware(originalPsbt)
        app.bitcoinBlockchain.broadcast(hwSignedOriginal).shouldBeOk()

        turbineScope(timeout = 60.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            awaitUntil { txs ->
              txs.any { it.id == hwSignedOriginal.id && it.confirmationStatus == Pending }
            }

            // First bump: 1 -> 3 sat/vB
            val bump1Psbt = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.FeeBump(
                txid = hwSignedOriginal.id,
                feeRate = FeeRate(3.0f)
              )
            ).shouldBeOk()

            bump1Psbt.fee.amount.fractionalUnitValue.longValue()
              .shouldBeGreaterThan(originalPsbt.fee.amount.fractionalUnitValue.longValue())
            // Verify RBF signaling is preserved so we can bump again
            bump1Psbt.inputs.any { it.sequence < 0xFFFFFFFEu }.shouldBeTrue()

            val hwSignedBump1 = app.signPsbtWithHardware(bump1Psbt)
            app.bitcoinBlockchain.broadcast(hwSignedBump1).shouldBeOk()

            spendingWallet.sync().shouldBeOk()
            awaitUntil { txs ->
              txs.any { it.id == hwSignedBump1.id && it.confirmationStatus == Pending }
            }

            // Second bump: 3 -> 10 sat/vB
            val bump2Psbt = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.FeeBump(
                txid = hwSignedBump1.id,
                feeRate = FeeRate(10.0f)
              )
            ).shouldBeOk()

            bump2Psbt.fee.amount.fractionalUnitValue.longValue()
              .shouldBeGreaterThan(bump1Psbt.fee.amount.fractionalUnitValue.longValue())

            val hwSignedBump2 = app.signPsbtWithHardware(bump2Psbt)
            app.bitcoinBlockchain.broadcast(hwSignedBump2).shouldBeOk()

            app.mineBlock()
            spendingWallet.sync().shouldBeOk()

            val finalTxs = awaitUntil { txs ->
              txs.any {
                it.id == hwSignedBump2.id &&
                  it.confirmationStatus is BitcoinTransaction.ConfirmationStatus.Confirmed
              }
            }

            // Final replacement should be confirmed
            val confirmedTx = finalTxs.single { it.id == hwSignedBump2.id }
            (confirmedTx.confirmationStatus is BitcoinTransaction.ConfirmationStatus.Confirmed)
              .shouldBeTrue()

            // Original and intermediate bumps should be gone
            finalTxs.none { it.id == hwSignedOriginal.id }.shouldBeTrue()
            finalTxs.none { it.id == hwSignedBump1.id }.shouldBeTrue()
          }
        }

        app.returnFundsToTreasury()
      }

    test("fails when transaction is already confirmed")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val fundingAmount = sats(50_000L)
        val sendAmount = sats(10_000L)

        app.addSomeFunds(amount = fundingAmount)
        app.waitForFunds { it.total == fundingAmount }

        val spendingWallet = app.getActiveWallet()
        val treasuryAddress = app.treasuryWallet.getReturnAddress()

        val originalPsbt = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.Regular(
            recipientAddress = treasuryAddress,
            amount = BitcoinTransactionSendAmount.ExactAmount(sendAmount),
            feePolicy = FeePolicy.Rate(FeeRate(1.0f))
          )
        ).shouldBeOk()

        val hwSignedOriginal = app.signPsbtWithHardware(originalPsbt)
        app.bitcoinBlockchain.broadcast(hwSignedOriginal).shouldBeOk()

        // Mine a block to confirm the transaction
        app.mineBlock()

        turbineScope(timeout = 30.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            awaitUntil { txs ->
              txs.any {
                it.id == hwSignedOriginal.id &&
                  it.confirmationStatus is BitcoinTransaction.ConfirmationStatus.Confirmed
              }
            }

            // Attempt to bump fee on confirmed transaction should fail
            val bumpResult = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.FeeBump(
                txid = hwSignedOriginal.id,
                feeRate = FeeRate(5.0f)
              )
            )

            bumpResult.shouldBeErrOfType<BdkError.Generic>()
          }
        }

        app.returnFundsToTreasury()
      }

    test("exact amount preserves recipient amount")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val fundingAmount = sats(100_000L)
        val sendAmount = sats(10_000L)

        app.addSomeFunds(amount = fundingAmount)
        app.waitForFunds { it.total == fundingAmount }

        val spendingWallet = app.getActiveWallet()
        val treasuryAddress = app.treasuryWallet.getReturnAddress()

        val originalPsbt = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.Regular(
            recipientAddress = treasuryAddress,
            amount = BitcoinTransactionSendAmount.ExactAmount(sendAmount),
            feePolicy = FeePolicy.Rate(FeeRate(1.0f))
          )
        ).shouldBeOk()

        val hwSignedOriginal = app.signPsbtWithHardware(originalPsbt)
        app.bitcoinBlockchain.broadcast(hwSignedOriginal).shouldBeOk()

        turbineScope(timeout = 30.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            awaitUntil { txs ->
              txs.any { it.id == hwSignedOriginal.id && it.confirmationStatus == Pending }
            }

            val bumpFeePsbt = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.FeeBump(
                txid = hwSignedOriginal.id,
                feeRate = FeeRate(5.0f)
              )
            ).shouldBeOk()

            // Verify recipient amount is preserved (fee comes from change reduction)
            bumpFeePsbt.amountSats.shouldBe(originalPsbt.amountSats)
            bumpFeePsbt.fee.amount.fractionalUnitValue.longValue()
              .shouldBeGreaterThan(originalPsbt.fee.amount.fractionalUnitValue.longValue())

            val hwSignedReplacement = app.signPsbtWithHardware(bumpFeePsbt)
            app.bitcoinBlockchain.broadcast(hwSignedReplacement).shouldBeOk()
          }
        }

        app.returnFundsToTreasury()
      }
  }

  context("FeeBumpWithDrain - sweeps and consolidations") {

    test("fee bump consolidation reduces output to cover higher fee")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val utxoAmount1 = sats(10_000L)
        val utxoAmount2 = sats(15_000L)

        // Fund wallet with multiple UTXOs
        app.addSomeFunds(amount = utxoAmount1)
        app.waitForFunds { it.total == utxoAmount1 }

        app.addSomeFunds(amount = utxoAmount2)
        app.waitForFunds { it.total == utxoAmount1 + utxoAmount2 }

        val spendingWallet = app.getActiveWallet()
        val selfAddress = spendingWallet.getNewAddress().shouldBeOk()

        // Create consolidation (sends all to self) with low fee
        val originalPsbt = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.Regular(
            recipientAddress = selfAddress,
            amount = BitcoinTransactionSendAmount.SendAll,
            feePolicy = FeePolicy.Rate(FeeRate(1.0f))
          )
        ).shouldBeOk()

        originalPsbt.numOfInputs.shouldBe(2)

        val hwSignedPsbt = app.signPsbtWithHardware(originalPsbt)
        app.bitcoinBlockchain.broadcast(hwSignedPsbt).shouldBeOk()

        turbineScope(timeout = 30.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            val txs = awaitUntil { txs ->
              txs.any { it.id == hwSignedPsbt.id && it.confirmationStatus == Pending }
            }

            val pendingTx = txs.single { it.id == hwSignedPsbt.id }
            val outputScript = pendingTx.outputs.single().scriptPubkey

            // Use FeeBumpWithDrain which uses BumpFeeTxBuilder.drainTo() to shrink the output
            val feeBumpWithDrainPsbt = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.FeeBumpWithDrain(
                txid = pendingTx.id,
                feeRate = FeeRate(5.0f),
                drainToScript = outputScript
              )
            ).shouldBeOk()

            feeBumpWithDrainPsbt.should {
              // Should have same inputs as original
              it.numOfInputs.shouldBe(2)
              // Should have higher fee
              it.fee.amount.fractionalUnitValue.longValue()
                .shouldBeGreaterThan(pendingTx.fee.shouldNotBeNull().fractionalUnitValue.longValue())
              // Should preserve RBF signaling
              it.inputs.any { input -> input.sequence < 0xFFFFFFFEu }.shouldBeTrue()
            }

            val hwSignedReplacement = app.signPsbtWithHardware(feeBumpWithDrainPsbt)
            app.bitcoinBlockchain.broadcast(hwSignedReplacement).shouldBeOk()
          }
        }

        app.returnFundsToTreasury()
      }

    test("fee bump with drain does not sweep extra confirmed UTXOs")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        // Fund wallet with 3 separate confirmed UTXOs
        val utxoAmount1 = sats(5_000L)
        val utxoAmount2 = sats(7_000L)
        val utxoAmount3 = sats(8_000L)

        app.addSomeFunds(amount = utxoAmount1)
        app.waitForFunds { it.total == utxoAmount1 }

        app.addSomeFunds(amount = utxoAmount2)
        app.waitForFunds { it.total == utxoAmount1 + utxoAmount2 }

        app.addSomeFunds(amount = utxoAmount3)
        app.waitForFunds { it.total == utxoAmount1 + utxoAmount2 + utxoAmount3 }

        val spendingWallet = app.getActiveWallet()
        spendingWallet.sync().shouldBeOk()

        // Get all 3 UTXOs
        val allUtxos = spendingWallet.unspentOutputs().first()
        allUtxos.shouldHaveSize(3)

        // Select only 2 UTXOs for the original transaction (sorted by value for determinism)
        val sortedUtxos = allUtxos.sortedBy { it.txOut.value }
        val utxosToUse = sortedUtxos.take(2).toSet()
        val selfAddress = spendingWallet.getNewAddress().shouldBeOk()

        // Create original transaction draining only the 2 selected UTXOs
        val originalPsbt = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.DrainAllFromUtxos(
            recipientAddress = selfAddress,
            feePolicy = FeePolicy.Rate(FeeRate(1.0f)),
            utxos = utxosToUse
          )
        ).shouldBeOk()

        // Verify original uses exactly 2 inputs
        originalPsbt.numOfInputs.shouldBe(2)
        val originalOutpoints = originalPsbt.inputs.map { it.outpoint }.toSet()
        originalOutpoints.shouldHaveSize(2)

        val hwSignedOriginal = app.signPsbtWithHardware(originalPsbt)
        app.bitcoinBlockchain.broadcast(hwSignedOriginal).shouldBeOk()

        turbineScope(timeout = 30.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            val txs = awaitUntil { txs ->
              txs.any { it.id == hwSignedOriginal.id && it.confirmationStatus == Pending }
            }

            val pendingTx = txs.single { it.id == hwSignedOriginal.id }
            val outputScript = pendingTx.outputs.single().scriptPubkey

            // Fee bump using FeeBumpWithDrain
            val feeBumpPsbt = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.FeeBumpWithDrain(
                txid = pendingTx.id,
                feeRate = FeeRate(5.0f),
                drainToScript = outputScript
              )
            ).shouldBeOk()

            // Verify the fee bump uses ONLY the original 2 inputs, not the 3rd UTXO
            feeBumpPsbt.numOfInputs.shouldBe(2)
            val bumpedOutpoints = feeBumpPsbt.inputs.map { it.outpoint }.toSet()
            bumpedOutpoints.shouldBe(originalOutpoints)

            // Verify higher fee
            feeBumpPsbt.fee.amount.fractionalUnitValue.longValue()
              .shouldBeGreaterThan(pendingTx.fee.shouldNotBeNull().fractionalUnitValue.longValue())

            // Verify RBF signaling preserved
            feeBumpPsbt.inputs.any { it.sequence < 0xFFFFFFFEu }.shouldBeTrue()

            val hwSignedReplacement = app.signPsbtWithHardware(feeBumpPsbt)
            app.bitcoinBlockchain.broadcast(hwSignedReplacement).shouldBeOk()

            // Sync after RBF replacement so wallet sees the new transaction
            spendingWallet.sync().shouldBeOk()
          }
        }

        app.returnFundsToTreasury()
      }

    test("fee bump with drain shrinks external sweep output and preserves script")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val fundingAmount = sats(25_000L)

        app.addSomeFunds(amount = fundingAmount)
        app.waitForFunds { it.total == fundingAmount }

        val spendingWallet = app.getActiveWallet()
        val treasuryAddress = app.treasuryWallet.getReturnAddress()

        val originalPsbt = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.Regular(
            recipientAddress = treasuryAddress,
            amount = BitcoinTransactionSendAmount.SendAll,
            feePolicy = FeePolicy.Rate(FeeRate(1.0f))
          )
        ).shouldBeOk()

        originalPsbt.outputs.shouldHaveSize(1)
        val originalOutputScript = originalPsbt.outputs.single().scriptPubkey
        val originalAmountSats = originalPsbt.amountSats.toLong()

        val hwSignedOriginal = app.signPsbtWithHardware(originalPsbt)
        app.bitcoinBlockchain.broadcast(hwSignedOriginal).shouldBeOk()

        turbineScope(timeout = 30.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            val txs = awaitUntil { txs ->
              txs.any { it.id == hwSignedOriginal.id && it.confirmationStatus == Pending }
            }

            val pendingTx = txs.single { it.id == hwSignedOriginal.id }
            val outputScript = pendingTx.outputs.single().scriptPubkey

            val bumpFeePsbt = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.FeeBumpWithDrain(
                txid = pendingTx.id,
                feeRate = FeeRate(5.0f),
                drainToScript = outputScript
              )
            ).shouldBeOk()

            bumpFeePsbt.outputs.shouldHaveSize(1)
            bumpFeePsbt.outputs.single().scriptPubkey.shouldBe(outputScript)
            outputScript.shouldBe(originalOutputScript)

            bumpFeePsbt.amountSats.toLong().shouldBeLessThan(originalAmountSats)
            bumpFeePsbt.fee.amount.fractionalUnitValue.longValue()
              .shouldBeGreaterThan(originalPsbt.fee.amount.fractionalUnitValue.longValue())

            val hwSignedReplacement = app.signPsbtWithHardware(bumpFeePsbt)
            app.bitcoinBlockchain.broadcast(hwSignedReplacement).shouldBeOk()

            // Sync after RBF replacement so wallet sees the new transaction
            spendingWallet.sync().shouldBeOk()

            awaitUntil { replacementTxs ->
              replacementTxs.any { it.id == hwSignedReplacement.id && it.confirmationStatus == Pending }
            }
          }
        }

        app.returnFundsToTreasury()
      }

    test("fee bump with drain fails with insufficient funds when output would be below dust limit")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val fundingAmount = sats(2_000L)

        app.addSomeFunds(amount = fundingAmount)
        app.waitForFunds { it.total == fundingAmount }

        val spendingWallet = app.getActiveWallet()
        val selfAddress = spendingWallet.getNewAddress().shouldBeOk()

        val originalPsbt = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.Regular(
            recipientAddress = selfAddress,
            amount = BitcoinTransactionSendAmount.SendAll,
            feePolicy = FeePolicy.Rate(FeeRate(1.0f))
          )
        ).shouldBeOk()

        val hwSignedOriginal = app.signPsbtWithHardware(originalPsbt)
        app.bitcoinBlockchain.broadcast(hwSignedOriginal).shouldBeOk()

        turbineScope(timeout = 30.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            val txs = awaitUntil { txs ->
              txs.any { it.id == hwSignedOriginal.id && it.confirmationStatus == Pending }
            }

            val pendingTx = txs.single { it.id == hwSignedOriginal.id }
            val originalFee =
              pendingTx.fee.shouldNotBeNull().fractionalUnitValue.longValue()
            val originalOutput = pendingTx.outputs.single().value.toLong()
            val vsize = pendingTx.vsize.shouldNotBeNull().toLong()
            val inputSum = originalFee + originalOutput
            // Intentionally below any reasonable dust threshold, independent of relay fee and script type.
            val targetOutput = 1L
            val targetFee = inputSum - targetOutput
            val targetFeeRate = FeeRate(targetFee.toFloat() / vsize.toFloat())

            val bumpResult = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.FeeBumpWithDrain(
                txid = pendingTx.id,
                feeRate = targetFeeRate,
                drainToScript = pendingTx.outputs.single().scriptPubkey
              )
            )

            bumpResult.shouldBeErrOfType<BdkError.InsufficientFunds>()
          }
        }

        app.returnFundsToTreasury()
      }
  }
})
