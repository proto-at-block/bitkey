package build.wallet.bitcoin.transactions

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bdk.bindings.BdkScript
import build.wallet.bdk.bindings.BdkTxIn
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.bitcoin.wallet.SpendingWalletV2Error
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.flags.setBdk2Enabled
import build.wallet.money.BitcoinMoney
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
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class FeeBumpFunctionalTests : FunSpec({

  // Valid P2WPKH scriptPubKey for error path tests: OP_0 <20-byte-pubkey-hash>
  val fakeP2WPKHScript = object : BdkScript {
    override val rawOutputScript: List<UByte> = listOf(
      0x00u, 0x14u,
      0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u,
      0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u, 0x00u
    )
  }

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

  context("ManualFeeBump - sweeps with output shrinking") {
    test("creates valid PSBT with inputs from pending sweep transaction")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val fundingAmount = sats(50_000L)

        app.addSomeFunds(amount = fundingAmount)
        app.waitForFunds { it.total == fundingAmount }

        val spendingWallet = app.getActiveWallet()

        val treasuryAddress = app.treasuryWallet.getReturnAddress()

        // Create a sweep transaction (SendAll)
        val originalPsbt = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.Regular(
            recipientAddress = treasuryAddress,
            amount = BitcoinTransactionSendAmount.SendAll,
            feePolicy = FeePolicy.Rate(FeeRate(1.0f))
          )
        ).shouldBeOk()

        originalPsbt.numOfInputs.shouldBe(1)

        val hwSignedPsbt = app.signPsbtWithHardware(originalPsbt)
        app.bitcoinBlockchain.broadcast(hwSignedPsbt).shouldBeOk()

        turbineScope(timeout = 30.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            val txs = awaitUntil { txs ->
              txs.any { it.id == hwSignedPsbt.id && it.confirmationStatus == Pending }
            }

            val pendingTx = txs.single { it.id == hwSignedPsbt.id }
            val originalInputs = pendingTx.inputs.toList()

            val outputScript = pendingTx.outputs.single().scriptPubkey

            val originalFeeSats = pendingTx.fee.shouldNotBeNull()
              .fractionalUnitValue.longValue()
            val newFeeSats = originalFeeSats * 3

            // Create ManualFeeBump PSBT
            val manualFeeBumpPsbt = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.ManualFeeBump(
                originalInputs = originalInputs,
                outputScript = outputScript,
                absoluteFee = Fee(BitcoinMoney.sats(newFeeSats))
              )
            ).shouldBeOk()

            manualFeeBumpPsbt.should {
              it.numOfInputs.shouldBe(originalInputs.size)
              it.fee.amount.fractionalUnitValue.longValue().shouldBe(newFeeSats)
              // Verify RBF signaling is preserved (sequence < 0xFFFFFFFE)
              it.inputs.any { input -> input.sequence < 0xFFFFFFFEu }.shouldBeTrue()
            }

            // Verify output shrinking
            val originalOutputSats = pendingTx.outputs.single().value.toLong()
            val expectedOutputSats = originalOutputSats - (newFeeSats - originalFeeSats)
            manualFeeBumpPsbt.amountSats.toLong().shouldBe(expectedOutputSats)

            val hwSignedReplacement = app.signPsbtWithHardware(manualFeeBumpPsbt)
            app.bitcoinBlockchain.broadcast(hwSignedReplacement).shouldBeOk()
          }
        }

        app.returnFundsToTreasury()
      }

    test("handles multiple inputs from pending consolidation transaction")
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

        // Create consolidation (sends all to self)
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
            val originalInputs = pendingTx.inputs.toList()
            originalInputs.size.shouldBe(2)

            val outputScript = pendingTx.outputs.single().scriptPubkey

            val originalFeeSats = pendingTx.fee.shouldNotBeNull()
              .fractionalUnitValue.longValue()
            val newFeeSats = originalFeeSats * 2

            val manualFeeBumpPsbt = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.ManualFeeBump(
                originalInputs = originalInputs,
                outputScript = outputScript,
                absoluteFee = Fee(BitcoinMoney.sats(newFeeSats))
              )
            ).shouldBeOk()

            manualFeeBumpPsbt.should {
              it.numOfInputs.shouldBe(2)
              it.fee.amount.fractionalUnitValue.longValue().shouldBe(newFeeSats)
              // Verify RBF signaling is preserved (sequence < 0xFFFFFFFE)
              it.inputs.any { input -> input.sequence < 0xFFFFFFFEu }.shouldBeTrue()
            }

            // Verify output shrinking
            // Note: For self-consolidation, amountSats is 0 since all outputs are "mine".
            // Instead, verify the actual output value in the PSBT.
            val originalOutputSats = pendingTx.outputs.single().value.toLong()
            val expectedOutputSats = originalOutputSats - (newFeeSats - originalFeeSats)
            val actualOutputSats = manualFeeBumpPsbt.outputs.single().value.toLong()
            actualOutputSats.shouldBe(expectedOutputSats)

            val hwSignedReplacement = app.signPsbtWithHardware(manualFeeBumpPsbt)
            app.bitcoinBlockchain.broadcast(hwSignedReplacement).shouldBeOk()
          }
        }

        app.returnFundsToTreasury()
      }

    test("fails when txid not in wallet graph")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val fundingAmount = sats(50_000L)

        app.addSomeFunds(amount = fundingAmount)
        app.waitForFunds { it.total == fundingAmount }

        val spendingWallet = app.getActiveWallet()

        // Fabricate inputs with a non-existent txid
        val fakeInputs = listOf(
          BdkTxIn(
            outpoint = BdkOutPoint(
              txid = "abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234abcd1234",
              vout = 0u
            ),
            sequence = 0xFFFFFFFDu, // RBF enabled
            witness = emptyList()
          )
        )

        val result = spendingWallet.createSignedPsbt(
          PsbtConstructionMethod.ManualFeeBump(
            originalInputs = fakeInputs,
            outputScript = fakeP2WPKHScript,
            absoluteFee = Fee(BitcoinMoney.sats(1000L))
          )
        )

        result.shouldBeErrOfType<SpendingWalletV2Error.PreviousTransactionNotFound>()

        app.returnFundsToTreasury()
      }

    test("fails when vout index is out of bounds")
      .config(tags = setOf(IsolatedTest)) {
        val app = launchNewApp()
        app.bdk2FeatureFlag.setBdk2Enabled(true)
        app.onboardFullAccountWithFakeHardware()

        val fundingAmount = sats(50_000L)

        app.addSomeFunds(amount = fundingAmount)
        app.waitForFunds { it.total == fundingAmount }

        val spendingWallet = app.getActiveWallet()

        turbineScope(timeout = 30.seconds) {
          spendingWallet.transactions().test {
            spendingWallet.sync().shouldBeOk()

            // Get a real transaction from the wallet graph (the funding tx)
            val txs = awaitUntil { it.isNotEmpty() }
            val realTxid = txs.first().id

            // Use real txid but invalid vout index
            val fakeInputs = listOf(
              BdkTxIn(
                outpoint = BdkOutPoint(
                  txid = realTxid,
                  vout = 999u // Invalid - transaction won't have this many outputs
                ),
                sequence = 0xFFFFFFFDu,
                witness = emptyList()
              )
            )

            val result = spendingWallet.createSignedPsbt(
              PsbtConstructionMethod.ManualFeeBump(
                originalInputs = fakeInputs,
                outputScript = fakeP2WPKHScript,
                absoluteFee = Fee(BitcoinMoney.sats(1000L))
              )
            )

            result.shouldBeErrOfType<SpendingWalletV2Error.PreviousOutputNotFound>()

            cancelAndConsumeRemainingEvents()
          }
        }

        app.returnFundsToTreasury()
      }
  }
})
