package build.wallet.utxo

import app.cash.turbine.test
import build.wallet.bitcoin.bdk.bitcoinAmount
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.UtxoConsolidation
import build.wallet.bitcoin.transactions.getTransactionData
import build.wallet.bitcoin.utxo.NotEnoughUtxosToConsolidateError
import build.wallet.bitcoin.utxo.UtxoConsolidationType.ConsolidateAll
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class UtxoConsolidationFunctionalTests : FunSpec({
  context("prepareUtxoConsolidation") {
    xtest("error is returned when wallet has 0 UTXOs") {
      val app = launchNewApp()
      app.onboardFullAccountWithFakeHardware()

      app.utxoConsolidationService
        .prepareUtxoConsolidation()
        .shouldBeErrOfType<NotEnoughUtxosToConsolidateError>()
    }

    xtest("error is returned when wallet has 1 UTXO") {
      val app = launchNewApp()
      app.onboardFullAccountWithFakeHardware()

      app.addSomeFunds(amount = sats(1_000L))
      app.waitForFunds()

      app.utxoConsolidationService
        .prepareUtxoConsolidation()
        .shouldBeErrOfType<NotEnoughUtxosToConsolidateError>()
    }

    xtest("UTXO consolidation is available when there are more than 1 UTXOs") {
      val app = launchNewApp()
      app.onboardFullAccountWithFakeHardware()

      app.addSomeFunds(amount = sats(2_000L))
      app.addSomeFunds(amount = sats(4_000L))
      app.waitForFunds { it.total == sats(6_000L) }

      val consolidationParams = app.utxoConsolidationService.prepareUtxoConsolidation()
        .shouldBeOk()
        .single()

      consolidationParams.should {
        it.type.shouldBe(ConsolidateAll)
        it.balance.shouldBe(sats(6_000L))
        it.eligibleUtxoCount.shouldBe(2)
        it.consolidationCost.isPositive.shouldBeTrue()
        it.walletHasUnconfirmedUtxos.shouldBeFalse()

        it.appSignedPsbt.fee.isPositive.shouldBeTrue()
        it.appSignedPsbt.numOfInputs.shouldBe(2)
      }

      val spendingWallet = app.getActiveWallet()
      // Target address of the consolidation transaction belongs to this wallet.
      spendingWallet.isMine(consolidationParams.targetAddress).shouldBeOk(true)
    }
  }

  xtest("consolidate all: consolidate two UTXOs into one UTXO") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    val consolidationAmountBeforeFee = sats(6_000L)

    app.addSomeFunds(amount = sats(2_000L))
    app.addSomeFunds(amount = sats(4_000L))
    app.waitForFunds { it.total == consolidationAmountBeforeFee }

    // UTXO consolidation is available without f8e access
    app.networkingDebugService.setFailF8eRequests(value = true)

    // Prepare UTXO consolidation
    val consolidationParams =
      app.utxoConsolidationService.prepareUtxoConsolidation().shouldBeOk().single()

    // Sign consolidation with hardware
    val appAndHardwareSignedPsbt = app.signPsbtWithHardware(consolidationParams.appSignedPsbt)

    // Complete consolidation by broadcasting it
    val consolidationTransactionDetail = app.utxoConsolidationService
      .broadcastConsolidation(appAndHardwareSignedPsbt)
      .shouldBeOk()

    // Consolidation transaction is listed in the transactions list
    val spendingWallet = app.getActiveWallet()
    spendingWallet.transactions().test {
      // Await until transactions contain the consolidation transaction
      val consolidationTransaction = awaitUntil {
        it.any { tx ->
          tx.id == consolidationTransactionDetail.broadcastDetail.transactionId
        }
      }.single { it.id == consolidationTransactionDetail.broadcastDetail.transactionId }

      val consolidationAmountAfterFee =
        consolidationAmountBeforeFee - consolidationParams.consolidationCost

      consolidationTransaction.should {
        it.confirmationStatus.shouldBe(Pending)
        it.transactionType.shouldBe(UtxoConsolidation)

        it.total.shouldBe(consolidationAmountBeforeFee)
        it.subtotal.shouldBe(consolidationAmountAfterFee)
        it.fee.shouldBe(consolidationParams.consolidationCost)

        it.estimatedConfirmationTime.shouldBe(consolidationTransactionDetail.estimatedConfirmationTime)

        // 2 UTXOs were consolidated into 1 UTXO
        it.inputs.shouldHaveSize(2)
        it.outputs.shouldHaveSize(1)

        // Validate UTXO
        val consolidationUtxo = it.outputs.single()
        consolidationUtxo.bitcoinAmount.shouldBe(consolidationAmountAfterFee)
      }

      spendingWallet.balance().first().total.shouldBe(consolidationAmountAfterFee)

      // The target address of the consolidation transaction belongs to this wallet.
      consolidationParams.targetAddress.shouldBe(consolidationTransaction.recipientAddress)
      spendingWallet
        .isMine(consolidationTransaction.recipientAddress.shouldNotBeNull())
        .shouldBeOk(true)
    }

    // Can spend funds
    app.returnFundsToTreasury()
  }

  /**
   * This test validates scenario when there is an unconfirmed incoming transaction.
   * A UTXO from this transaction should be ignored during consolidation.
   *
   * In this test we have 3 UTXOs:
   * - 2 UTXOs from confirmed transactions, 1,000 sats each
   * - 1 UTXO from an unconfirmed transaction, 3,000 sats
   *
   * We consolidate 2 UTXOs from confirmed transactions into 1 UTXO, 2,000 sats minus mining fee.
   * The UTXO from the unconfirmed transaction should be ignored.
   *
   * The wallet will have 2 UTXOs remaining: the consolidated UTXO and the UTXO from the unconfirmed transaction.
   * The total balance after consolidation should be 2,000 sats minus mining fee plus 3,000 sats.
   */
  xtest("consolidate all: ignore UTXO from incoming unconfirmed transaction") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    // An amount associated with an unconfirmed transaction. This UTXO will be ignored.
    val unconfirmedAmount = sats(3_000L)
    // A total amount associated with UTXOs from confirmed transactions. These UTXOs will be
    // consolidated.
    val confirmedAmount = sats(4_000L)

    // Add funds
    app.addSomeFunds(amount = sats(2_000))
    app.addSomeFunds(amount = sats(2_000))
    app.addSomeFunds(amount = unconfirmedAmount, waitForConfirmation = false)

    // Wait for balance
    val totalBalance = unconfirmedAmount + confirmedAmount
    // How much we will consolidate
    val consolidationAmount = confirmedAmount
    app.waitForFunds { it.total == totalBalance }.should {
      it.confirmed.shouldBe(consolidationAmount)
      it.untrustedPending.shouldBe(unconfirmedAmount)
    }

    // Prepare UTXO consolidation
    val consolidationParams =
      app.utxoConsolidationService.prepareUtxoConsolidation().shouldBeOk().single()

    consolidationParams.should {
      it.eligibleUtxoCount.shouldBe(2)
      it.balance.shouldBe(consolidationAmount)
      it.walletHasUnconfirmedUtxos.shouldBeTrue()
    }

    // Sign consolidation with hardware
    val appAndHardwareSignedPsbt = app
      .signPsbtWithHardware(consolidationParams.appSignedPsbt)

    // Complete consolidation by broadcasting it
    val consolidationTransactionDetail = app.utxoConsolidationService
      .broadcastConsolidation(appAndHardwareSignedPsbt)
      .shouldBeOk()

    // Consolidation transaction is listed in the transactions list
    val spendingWallet = app.getActiveWallet()
    spendingWallet.transactions().test {
      // Await until transactions contain the consolidation transaction
      val consolidationTransaction = awaitUntil {
        it.any { tx ->
          tx.id == consolidationTransactionDetail.broadcastDetail.transactionId
        }
      }.single { it.id == consolidationTransactionDetail.broadcastDetail.transactionId }

      // The total of the consolidation transaction minus the mining fee
      val consolidationAmountAfterFee =
        consolidationAmount - consolidationParams.consolidationCost

      // Validate the consolidation transaction
      consolidationTransaction.should {
        it.confirmationStatus.shouldBe(Pending)
        it.transactionType.shouldBe(UtxoConsolidation)

        it.total.shouldBe(consolidationAmount)
        it.subtotal.shouldBe(consolidationAmountAfterFee)
        it.fee.shouldBe(consolidationParams.consolidationCost)

        it.estimatedConfirmationTime.shouldBe(consolidationTransactionDetail.estimatedConfirmationTime)

        // 2 UTXOs were consolidated into 1 UTXO
        it.inputs.shouldHaveSize(2)
        it.outputs.shouldHaveSize(1)

        // Validate UTXO
        val consolidationUtxo = it.outputs.single()
        consolidationUtxo.bitcoinAmount.shouldBe(consolidationAmountAfterFee)
      }

      val balanceAfterConsolidation = consolidationAmountAfterFee + unconfirmedAmount
      app.shouldHaveTotalBalance(balanceAfterConsolidation)

      // The target address of the consolidation transaction belongs to this wallet.
      consolidationParams.targetAddress.shouldBe(consolidationTransaction.recipientAddress)
      spendingWallet
        .isMine(consolidationTransaction.recipientAddress.shouldNotBeNull())
        .shouldBeOk(true)

      // Have 2 remaining UTXOs: consolidated UTXO and a UTXO from the unconfirmed transaction.
      val transactionsData = app.bitcoinWalletService.getTransactionData()
      transactionsData.utxos.should { utxos ->
        utxos.confirmed.shouldBeEmpty() // Consolidation transaction is not confirmed yet.
        utxos.unconfirmed.shouldHaveSize(2)

        // UTXO from the consolidation transaction
        val consolidationUtxo =
          utxos.unconfirmed.single { it.outPoint.txid == consolidationTransaction.id }
        consolidationUtxo.bitcoinAmount.shouldBe(consolidationAmountAfterFee)

        // UTXO from the unconfirmed transaction
        utxos.unconfirmed.single { it.bitcoinAmount == unconfirmedAmount }
      }
    }

    // Can spend funds
    app.returnFundsToTreasury()
  }
})
