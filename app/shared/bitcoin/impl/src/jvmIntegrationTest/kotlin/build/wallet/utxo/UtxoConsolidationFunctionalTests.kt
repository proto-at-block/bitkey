package build.wallet.utxo

import app.cash.turbine.test
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.UtxoConsolidation
import build.wallet.bitcoin.utxo.NotEnoughUtxosToConsolidateError
import build.wallet.bitcoin.utxo.UtxoConsolidationService
import build.wallet.bitcoin.utxo.UtxoConsolidationType.ConsolidateAll
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.setFlagValue
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import build.wallet.time.truncateToMilliseconds
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class UtxoConsolidationFunctionalTests : FunSpec({

  lateinit var appTester: AppTester
  lateinit var utxoConsolidationService: UtxoConsolidationService

  beforeTest {
    appTester = launchNewApp()
    utxoConsolidationService = appTester.app.appComponent.utxoConsolidationService
  }

  context("prepareUtxoConsolidation") {
    test("error is returned when wallet has 0 UTXOs") {
      appTester.onboardFullAccountWithFakeHardware()

      utxoConsolidationService
        .prepareUtxoConsolidation()
        .shouldBeErrOfType<NotEnoughUtxosToConsolidateError>()
    }

    test("error is returned when wallet has 1 UTXO") {
      appTester.onboardFullAccountWithFakeHardware()

      appTester.addSomeFunds(amount = sats(1_000L))
      appTester.waitForFunds()

      utxoConsolidationService
        .prepareUtxoConsolidation()
        .shouldBeErrOfType<NotEnoughUtxosToConsolidateError>()
    }

    test("UTXO consolidation is available when there are more than 1 UTXOs") {
      appTester.onboardFullAccountWithFakeHardware()

      appTester.addSomeFunds(amount = sats(1_000L))
      appTester.addSomeFunds(amount = sats(2_000L))
      appTester.waitForFunds { it.total == sats(3_000L) }

      val consolidationParams = utxoConsolidationService.prepareUtxoConsolidation()
        .shouldBeOk()
        .single()

      consolidationParams.should {
        it.type.shouldBe(ConsolidateAll)
        it.balance.shouldBe(sats(3_000L))
        it.currentUtxoCount.shouldBe(2)
        it.consolidationCost.isPositive.shouldBeTrue()

        it.appSignedPsbt.fee.isPositive.shouldBeTrue()
        it.appSignedPsbt.numOfInputs.shouldBe(2)
      }

      val spendingWallet =
        appTester.app.appComponent.transactionsService.spendingWallet().filterNotNull().first()

      // Target address of the consolidation transaction belongs to this wallet.
      spendingWallet.isMine(consolidationParams.targetAddress).shouldBeOk(true)
    }
  }

  test("prepare and complete UTXO consolidation") {
    appTester.app.appComponent.utxoConsolidationFeatureFlag.setFlagValue(true)

    appTester.onboardFullAccountWithFakeHardware()

    val totalBalanceBeforeConsolidation = sats(3_000L)

    appTester.addSomeFunds(amount = sats(1_000L))
    appTester.addSomeFunds(amount = sats(2_000L))
    appTester.waitForFunds { it.total == totalBalanceBeforeConsolidation }

    // UTXO consolidation is available without f8e access
    appTester.app.appComponent.networkingDebugService.setFailF8eRequests(value = true)

    // Prepare UTXO consolidation
    val consolidationParams = utxoConsolidationService.prepareUtxoConsolidation().shouldBeOk().single()

    // Sign consolidation with hardware
    val appAndHardwareSignedPsbt = appTester
      .hardwareTransaction { session, commands ->
        commands.signTransaction(
          session = session,
          psbt = consolidationParams.appSignedPsbt,
          spendingKeyset = appTester.getActiveFullAccount().keybox.activeSpendingKeyset
        )
      }

    // Complete consolidation by broadcasting it
    val consolidationTransactionDetail = utxoConsolidationService
      .broadcastConsolidation(appAndHardwareSignedPsbt)
      .shouldBeOk()

    // Consolidation transaction is listed in the transactions list
    val spendingWallet =
      appTester.app.appComponent.transactionsService.spendingWallet().value.shouldNotBeNull()
    spendingWallet.transactions().test {
      // Await until transactions contain the consolidation transaction
      val consolidationTransaction = awaitUntil {
        it.any { tx ->
          tx.id == consolidationTransactionDetail.broadcastDetail.transactionId
        }
      }.single { it.id == consolidationTransactionDetail.broadcastDetail.transactionId }

      val totalBalanceAfterConsolidation =
        totalBalanceBeforeConsolidation - consolidationParams.consolidationCost

      consolidationTransaction.should {
        it.confirmationStatus.shouldBe(Pending)
        it.transactionType.shouldBe(UtxoConsolidation)

        it.total.shouldBe(totalBalanceBeforeConsolidation)
        it.subtotal.shouldBe(totalBalanceAfterConsolidation)
        it.fee.shouldBe(consolidationParams.consolidationCost)

        // The actual broadcast time before it's stored to database has nanoseconds precision.
        // When we store and read it from the database, we lose some of that precision.
        it.estimatedConfirmationTime.shouldBe(consolidationTransactionDetail.estimatedConfirmationTime.truncateToMilliseconds())

        // 2 UTXOs were consolidated into 1 UTXO
        it.inputs.shouldHaveSize(2)
        it.outputs.shouldHaveSize(1)
      }

      spendingWallet.balance().first().total.shouldBe(totalBalanceAfterConsolidation)

      // The target address of the consolidation transaction belongs to this wallet.
      consolidationParams.targetAddress.shouldBe(consolidationTransaction.recipientAddress)
      spendingWallet
        .isMine(consolidationTransaction.recipientAddress.shouldNotBeNull())
        .shouldBeOk(true)
    }

    // Can spend funds
    appTester.returnFundsToTreasury(appTester.getActiveFullAccount())
  }
})
