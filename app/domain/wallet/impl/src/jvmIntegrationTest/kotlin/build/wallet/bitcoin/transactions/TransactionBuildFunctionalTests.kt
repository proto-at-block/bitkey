package build.wallet.bitcoin.transactions

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import build.wallet.bitcoin.fees.FeePolicy
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.bitcoin.wallet.SpendingWallet.PsbtConstructionMethod
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.getActiveWallet
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.returnFundsToTreasury
import build.wallet.testing.ext.signPsbtWithHardware
import build.wallet.testing.ext.testForBdk1AndBdk2
import build.wallet.testing.ext.waitForFunds
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class TransactionBuildFunctionalTests : FunSpec({

  testForBdk1AndBdk2(
    "Regular send creates valid signed PSBT with exact amount",
    isIsolatedTest = true
  ) { app ->
    app.onboardFullAccountWithFakeHardware()

    val fundingAmount = sats(50_000L)
    val sendAmount = sats(10_000L)

    app.addSomeFunds(amount = fundingAmount)
    app.waitForFunds { it.total == fundingAmount }

    val spendingWallet = app.getActiveWallet()
    val treasuryAddress = app.treasuryWallet.getReturnAddress()

    val psbt = spendingWallet.createSignedPsbt(
      PsbtConstructionMethod.Regular(
        recipientAddress = treasuryAddress,
        amount = BitcoinTransactionSendAmount.ExactAmount(sendAmount),
        feePolicy = FeePolicy.Rate(FeeRate(5.0f))
      )
    ).shouldBeOk()

    psbt.should {
      it.amountSats.shouldBe(sendAmount.fractionalUnitValue.longValue().toULong())
      it.numOfInputs.shouldBeGreaterThan(0)
      it.fee.amount.fractionalUnitValue.longValue().shouldBeGreaterThan(0L)
    }

    val hwSignedPsbt = app.signPsbtWithHardware(psbt)
    app.bitcoinBlockchain.broadcast(hwSignedPsbt).shouldBeOk()

    turbineScope(timeout = 10.seconds) {
      spendingWallet.transactions().test {
        spendingWallet.sync().shouldBeOk()

        val txs = awaitUntil { txs ->
          txs.any { it.id == hwSignedPsbt.id && it.confirmationStatus == Pending }
        }

        val tx = txs.single { it.id == hwSignedPsbt.id }
        tx.should {
          it.confirmationStatus.shouldBe(Pending)
          it.transactionType.shouldBe(Outgoing)
          it.recipientAddress.shouldNotBeNull().shouldBe(treasuryAddress)
        }
      }
    }

    app.returnFundsToTreasury()
  }

  testForBdk1AndBdk2(
    "SendAll drains entire wallet balance minus fees",
    isIsolatedTest = true
  ) { app ->
    app.onboardFullAccountWithFakeHardware()

    val fundingAmount = sats(30_000L)

    app.addSomeFunds(amount = fundingAmount)
    app.waitForFunds { it.total == fundingAmount }

    val spendingWallet = app.getActiveWallet()
    val treasuryAddress = app.treasuryWallet.getReturnAddress()

    val psbt = spendingWallet.createSignedPsbt(
      PsbtConstructionMethod.Regular(
        recipientAddress = treasuryAddress,
        amount = BitcoinTransactionSendAmount.SendAll,
        feePolicy = FeePolicy.Rate(FeeRate(2.0f))
      )
    ).shouldBeOk()

    val expectedSendAmount = fundingAmount - psbt.fee.amount
    psbt.amountSats.shouldBe(expectedSendAmount.fractionalUnitValue.longValue().toULong())

    val hwSignedPsbt = app.signPsbtWithHardware(psbt)
    app.bitcoinBlockchain.broadcast(hwSignedPsbt).shouldBeOk()

    turbineScope(timeout = 10.seconds) {
      spendingWallet.balance().test {
        spendingWallet.sync().shouldBeOk()

        awaitUntil { balance ->
          balance.spendable == sats(0)
        }
      }
    }
  }

  testForBdk1AndBdk2(
    "DrainAllFromUtxos consolidates specific UTXOs",
    isIsolatedTest = true
  ) { app ->
    app.onboardFullAccountWithFakeHardware()

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

    val allUtxos = spendingWallet.unspentOutputs().first()
    allUtxos.shouldHaveSize(3)

    // Sort by value so selection is deterministic even if wallet returns UTXOs in arbitrary order
    val sortedUtxos = allUtxos.sortedBy { it.txOut.value }
    val utxosToConsolidate = sortedUtxos.take(2).toSet()
    // Use treasury address so amountSats calculation works (filters out "mine" outputs)
    val treasuryAddress = app.treasuryWallet.getReturnAddress()
    val totalInputValue = utxosToConsolidate.sumOf { it.txOut.value }

    val psbt = spendingWallet.createSignedPsbt(
      PsbtConstructionMethod.DrainAllFromUtxos(
        recipientAddress = treasuryAddress,
        feePolicy = FeePolicy.Rate(FeeRate(1.0f)),
        utxos = utxosToConsolidate
      )
    ).shouldBeOk()

    psbt.numOfInputs.shouldBe(2)
    val expectedOutputAmount = totalInputValue - psbt.fee.amount.fractionalUnitValue.longValue().toULong()
    psbt.amountSats.shouldBe(expectedOutputAmount)

    val hwSignedPsbt = app.signPsbtWithHardware(psbt)
    app.bitcoinBlockchain.broadcast(hwSignedPsbt).shouldBeOk()

    turbineScope(timeout = 10.seconds) {
      spendingWallet.transactions().test {
        spendingWallet.sync().shouldBeOk()

        awaitUntil { txs ->
          txs.any { it.id == hwSignedPsbt.id }
        }
      }
    }

    app.returnFundsToTreasury()
  }
})
