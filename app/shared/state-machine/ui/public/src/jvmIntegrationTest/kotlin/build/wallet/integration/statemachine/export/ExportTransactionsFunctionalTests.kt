package build.wallet.integration.statemachine.export

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_INSTRUCTIONS
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS
import build.wallet.bitcoin.export.*
import build.wallet.bitcoin.export.ExportTransactionRow.ExportTransactionType.*
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.feature.setFlagValue
import build.wallet.integration.statemachine.recovery.RecoveryTestingStateMachine
import build.wallet.integration.statemachine.recovery.RecoveryTestingTrackerScreenId.RECOVERY_COMPLETED
import build.wallet.money.BitcoinMoney.Companion.btc
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.money.BitcoinMoney.Companion.zero
import build.wallet.money.matchers.shouldBeGreaterThan
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.inprogress.waiting.AppDelayNotifyInProgressBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import build.wallet.testing.shouldBeOk
import build.wallet.testing.tags.TestTag.IsolatedTest
import com.github.michaelbull.result.getOrThrow
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class ExportTransactionsFunctionalTests : FunSpec({
  lateinit var appTester: AppTester

  beforeTest {
    appTester = launchNewApp()
    appTester.app.appComponent.utxoConsolidationFeatureFlag.setFlagValue(true)
  }

  test("e2e – export external, internal, and consolidation transactions") {
    appTester.onboardFullAccountWithFakeHardware()

    // Make two incoming transactions
    val initialFundingAmount = btc(0.0001)
    val fundingTransaction1 = appTester.addSomeFunds(amount = initialFundingAmount)
    val fundingTransaction2 = appTester.addSomeFunds(amount = initialFundingAmount)
    appTester.waitForFunds { it.spendable == btc(0.0002) }

    // Consolidate the outputs to produce consolidation transaction
    val (consolidationParams, consolidationTransactionDetail) = appTester.consolidateAllUtxos()

    // External transaction by sending back to treasury.
    appTester.returnFundsToTreasury()
    appTester.waitForFunds { it.confirmed == zero() }

    val service = appTester.app.appComponent.exportTransactionsService

    val csvString = service.export().shouldBeOk().data.utf8()
    val deserializedRowsToAssert =
      ExportTransactionsAsCsvSerializerImpl().fromCsvString(value = csvString).shouldBeOk()
    // We should have:
    // (1) 2 receive transactions
    // (2) 1 consolidation transaction
    // (3) 1 send transaction
    deserializedRowsToAssert.count().shouldBe(4)

    // We check in reverse in which these transactions are created because that's how we sort
    // transactions.
    val sendTransaction = deserializedRowsToAssert[0]
    sendTransaction.should {
      it.transactionType.shouldBe(Outgoing)
    }

    val consolidationTransaction = deserializedRowsToAssert[1]
    consolidationTransaction.should {
      it.txid.value.shouldBeEqual(consolidationTransactionDetail.broadcastDetail.transactionId)
      it.transactionType.shouldBe(UtxoConsolidation)
      it.amount.shouldBeEqual(consolidationParams.balance - consolidationParams.consolidationCost)
      it.fees?.shouldBeEqual(consolidationParams.consolidationCost)
    }

    val receiveTransaction1 = deserializedRowsToAssert[2]
    receiveTransaction1.should {
      it.txid.value.shouldBeEqual(fundingTransaction2.tx.id)
      it.transactionType.shouldBe(Incoming)
      it.fees?.shouldBeEqual(fundingTransaction2.tx.fee)
      it.amount.shouldBeEqual(fundingTransaction2.tx.amountBtc)
    }

    val receiveTransaction2 = deserializedRowsToAssert[3]
    receiveTransaction2.should {
      it.txid.value.shouldBeEqual(fundingTransaction1.tx.id)
      it.transactionType.shouldBe(Incoming)
      it.fees?.shouldBeEqual(fundingTransaction1.tx.fee)
      it.amount.shouldBeEqual(fundingTransaction1.tx.amountBtc)
    }
  }

  test("e2e - export transactions including inactive keysets") {
    appTester.onboardFullAccountWithFakeHardware()

    // Make two incoming transactions
    val initialFundingAmount = btc(0.0001)
    val fundingTransaction1 = appTester.addSomeFunds(amount = initialFundingAmount)
    val fundingTransaction2 = appTester.addSomeFunds(amount = initialFundingAmount)
    appTester.waitForFunds { it.spendable == btc(0.0002) }

    // Consolidate the outputs to produce consolidation transaction
    val (consolidationParams, consolidationTransactionDetail) = appTester.consolidateAllUtxos()

    // We do a Lost App + Cloud recovery
    appTester.app.appDataDeleter.deleteAll().getOrThrow()
    appTester.app.cloudBackupDeleter.delete(cloudServiceProvider())
    appTester.deleteBackupsFromFakeCloud()
    val recoveryStateMachine =
      RecoveryTestingStateMachine(
        appTester.app.accountDataStateMachine,
        appTester.app.recoveringKeyboxUiStateMachine,
        appTester.app.recoverySyncer,
        appTester.app.appComponent.accountService
      )

    recoveryStateMachine.test(
      props = Unit,
      useVirtualTime = false,
      testTimeout = 20.seconds,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilScreenWithBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<FormBodyModel>(ENABLE_PUSH_NOTIFICATIONS)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<AppDelayNotifyInProgressBodyModel>(LOST_APP_DELAY_NOTIFY_PENDING)

      appTester.completeRecoveryDelayPeriodOnF8e()
      awaitUntilScreenWithBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_READY)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilScreenWithBody<FormBodyModel>(SAVE_CLOUD_BACKUP_INSTRUCTIONS)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitUntilScreenWithBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_SIGN_PSBTS_PROMPT)
        .clickPrimaryButton()

      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_BROADCASTING) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitUntilScreenWithBody<FormBodyModel>(LOST_APP_DELAY_NOTIFY_SWEEP_SUCCESS)
        .clickPrimaryButton()

      awaitUntilScreenWithBody<FormBodyModel>(RECOVERY_COMPLETED)

      eventually(
        eventuallyConfig {
          duration = 20.seconds
          interval = 1.seconds
          initialDelay = 1.seconds
        }
      ) {
        val activeWallet = appTester.getActiveWallet()
        activeWallet.sync().shouldBeOk()
        val balance = activeWallet.balance().first()

        // Let's mine a block if the sweep transaction is still unconfirmed.
        if (balance.confirmed.isZero) {
          val newWalletFundingTx = activeWallet.transactions().first().first()
          appTester.mineBlock(newWalletFundingTx.id)
        }

        balance.confirmed.shouldBeGreaterThan(sats(0))
      }
    }

    val sweepTransaction = appTester.getActiveWallet().transactions().first().first()
    val newWalletTransaction = appTester.addSomeFunds(amount = initialFundingAmount)

    val service = appTester.app.appComponent.exportTransactionsService
    val csvString = service.export().shouldBeOk().data.utf8()

    val deserializedRowsToAssert =
      ExportTransactionsAsCsvSerializerImpl().fromCsvString(value = csvString).shouldBeOk()
    // We should have:
    // (1) 1 receive transaction on our new wallet
    // (2) 1 sweep transaction (collapsed from 1 "outgoing" from our old wallet, and 1 "incoming" to our new wallet)
    // (3) 1 consolidation transaction on our old wallet
    // (4) 2 receive transactions on our old wallet
    deserializedRowsToAssert.count().shouldBe(5)

    // We check in reverse in which these transactions are created because that's how we sort
    // transactions.
    val newWalletReceiveTransaction = deserializedRowsToAssert[0]
    newWalletReceiveTransaction.should {
      it.txid.value.shouldBe(newWalletTransaction.tx.id)
      it.amount.shouldBe(newWalletTransaction.tx.amountBtc)
      it.fees.shouldBe(newWalletTransaction.tx.fee)
      it.transactionType.shouldBe(Incoming)
    }

    val sweepWalletTransaction = deserializedRowsToAssert[1]
    sweepWalletTransaction.should {
      it.txid.value.shouldBeEqual(sweepTransaction.id)
      it.transactionType.shouldBe(Sweep)
      it.amount.shouldBeEqual(sweepTransaction.subtotal)
    }

    val oldWalletConsolidationTransaction = deserializedRowsToAssert[2]
    oldWalletConsolidationTransaction.should {
      it.txid.value.shouldBeEqual(consolidationTransactionDetail.broadcastDetail.transactionId)
      it.transactionType.shouldBe(UtxoConsolidation)
      it.amount.shouldBeEqual(consolidationParams.balance - consolidationParams.consolidationCost)
      it.fees?.shouldBeEqual(consolidationParams.consolidationCost)
    }

    val oldWalletReceiveTransaction2 = deserializedRowsToAssert[3]
    oldWalletReceiveTransaction2.should {
      it.txid.value.shouldBeEqual(fundingTransaction2.tx.id)
      it.transactionType.shouldBe(Incoming)
      it.fees?.shouldBeEqual(fundingTransaction2.tx.fee)
      it.amount.shouldBeEqual(fundingTransaction2.tx.amountBtc)
    }

    val oldWalletReceiveTransaction1 = deserializedRowsToAssert[4]
    oldWalletReceiveTransaction1.should {
      it.txid.value.shouldBeEqual(fundingTransaction1.tx.id)
      it.transactionType.shouldBe(Incoming)
      it.fees?.shouldBeEqual(fundingTransaction1.tx.fee)
      it.amount.shouldBeEqual(fundingTransaction1.tx.amountBtc)
    }
  }

  test("export with no transactions") {
    appTester.onboardFullAccountWithFakeHardware()

    val service = appTester.app.appComponent.exportTransactionsService
    val dataList = service.export().shouldBeOk().data.utf8().split("\n")
    // We should have:
    // (1) 1 heading row
    dataList.count().shouldBe(1)
  }

  test("export with pending transaction")
    .config(tags = setOf(IsolatedTest)) {

      // Make pending transaction
      appTester.onboardFullAccountWithFakeHardware()
      appTester.addSomeFunds(amount = sats(50000), waitForConfirmation = false)

      val service = appTester.app.appComponent.exportTransactionsService
      val dataList = service.export().shouldBeOk().data.utf8().split("\n")
      // We should have:
      // (1) 1 heading row
      dataList.count().shouldBe(1)
    }
})
