package build.wallet.integration.statemachine.recovery

import app.cash.turbine.test
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.events.screen.id.MoneyHomeEventTrackerScreenId.MONEY_HOME
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId.SETTINGS
import build.wallet.analytics.events.screen.id.UtxoConsolidationEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.WalletMigrationEventTrackerScreenId.*
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.feature.FeatureFlagValue
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.TransferConfirmationScreenVariant
import build.wallet.statemachine.send.TransferInitiatedBodyModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilSheet
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickPrivateWalletUpdateRow
import build.wallet.statemachine.ui.robots.clickSettings
import build.wallet.statemachine.utxo.TapAndHoldToConsolidateUtxosBodyModel
import build.wallet.statemachine.utxo.UtxoConsolidationConfirmationModel
import build.wallet.statemachine.utxo.UtxoConsolidationTransactionSentModel
import build.wallet.statemachine.walletmigration.FeeEstimateData
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationCompleteBodyModel
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationFeeEstimateSheetModel
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationIntroBodyModel
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationPendingTransactionsWarningSheetModel
import build.wallet.statemachine.walletmigration.PrivateWalletMigrationUtxoConsolidationRequiredSheetModel
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class PrivateWalletUpgradeFunctionalTests : FunSpec({
  test("happy path - onboard with legacy wallet, then upgrade to private wallet.") {
    val app = launchLegacyAppReadyForMigration()
    app.addSomeFunds(BitcoinMoney.sats(50_000), waitForConfirmation = true)

    lateinit var upgradeDestinationAddress: BitcoinAddress
    app.appUiStateMachine.test(Unit) {
      awaitUntilBody<MoneyHomeBodyModel>(MONEY_HOME) {
        clickSettings()
      }

      awaitUntilBody<SettingsBodyModel>(
        SETTINGS,
        matching = { bodyModel ->
          bodyModel.sectionModels[1].rowModels.map { it.title }.contains("Private Wallet Update")
        }
      ) {
        clickPrivateWalletUpdateRow()
      }

      awaitUntilBody<PrivateWalletMigrationIntroBodyModel> {
        clickPrimaryButton()
      }

      awaitUntilSheet<PrivateWalletMigrationFeeEstimateSheetModel>(
        matching = { it.feeEstimateData is FeeEstimateData.Loaded }
      ) {
        val loaded = feeEstimateData as FeeEstimateData.Loaded
        loaded.estimatedFee.shouldNotBeBlank()
        loaded.estimatedFeeSats.shouldNotBeBlank()
        onConfirm()
      }

      awaitUntilBody<SaveBackupInstructionsBodyModel> {
        onBackupClick()
      }

      awaitUntilBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING) {
        signInSuccess(CloudStoreAccount1Fake)
      }

      awaitUntilBody<LoadingSuccessBodyModel>(id = PRIVATE_WALLET_MIGRATION_SWEEP_GENERATING_PSBTS)

      awaitUntilBody<TransferConfirmationScreenModel>(
        matching = { it.variant == TransferConfirmationScreenVariant.PrivateWalletMigration }
      ) {
        clickPrimaryButton()
      }

      awaitUntilBody<LoadingSuccessBodyModel>(id = PRIVATE_WALLET_MIGRATION_SWEEP_BROADCASTING)
      awaitUntilBody<TransferInitiatedBodyModel>(id = PRIVATE_WALLET_MIGRATION_SWEEP_SUCCESS) {
        upgradeDestinationAddress = recipientAddress
        clickPrimaryButton()
      }

      awaitUntilBody<PrivateWalletMigrationCompleteBodyModel> {
        clickPrimaryButton()
      }

      awaitUntilBody<SettingsBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    app.accountService.activeAccount().test {
      val account = awaitItem()
      account.shouldBeTypeOf<FullAccount>()
      account.keybox.isPrivateWallet.shouldBeTrue()
      account.keybox.canUseKeyboxKeysets.shouldBeTrue()
    }
    app.bitcoinWalletService.transactionsData().test {
      val transactionData = awaitItem()
      transactionData.shouldNotBeNull()

      transactionData.transactions.size.shouldBe(1)
      val upgradeTransaction = transactionData.transactions.first()
      upgradeTransaction.recipientAddress.shouldBe(upgradeDestinationAddress)
    }
  }

  test("insufficient funds prevents confirming migration") {
    val app = launchLegacyAppReadyForMigration()

    app.appUiStateMachine.test(Unit) {
      awaitUntilBody<MoneyHomeBodyModel>(MONEY_HOME) {
        clickSettings()
      }

      awaitUntilBody<SettingsBodyModel>(SETTINGS, matching = { bodyModel ->
        bodyModel.sectionModels[1].rowModels.map { it.title }.contains("Private Wallet Update")
      }) {
        clickPrivateWalletUpdateRow()
      }

      awaitUntilBody<PrivateWalletMigrationIntroBodyModel> {
        clickPrimaryButton()
      }

      awaitUntilSheet<PrivateWalletMigrationFeeEstimateSheetModel>(
        matching = { it.feeEstimateData is FeeEstimateData.InsufficientFunds }
      ) {
        feeEstimateData.shouldBeInstanceOf<FeeEstimateData.InsufficientFunds>()
        onConfirm()
      }

      awaitUntilBody<SaveBackupInstructionsBodyModel> {
        onBackupClick()
      }

      awaitUntilBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING) {
        signInSuccess(CloudStoreAccount1Fake)
      }

      awaitUntilBody<PrivateWalletMigrationCompleteBodyModel> {
        clickPrimaryButton()
      }

      awaitUntilBody<SettingsBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    app.accountService.activeAccount().test {
      val account = awaitItem()
      account.shouldBeTypeOf<FullAccount>()
      account.keybox.isPrivateWallet.shouldBeTrue()
      account.keybox.canUseKeyboxKeysets.shouldBeTrue()
    }
  }

  test("too many utxos should ask for consolidation first") {
    // Create a legacy wallet, funded with two outputs.
    val app = launchLegacyAppReadyForMigration()
    repeat(3) {
      app.addSomeFunds(BitcoinMoney.sats(10_000), waitForConfirmation = true)
    }

    // Override the value in utxoMaxConsolidationCountFeatureFlag to 2 so we always land on the UTXO consolidation flow.
    app.utxoMaxConsolidationCountFeatureFlag.setFlagValue(FeatureFlagValue.DoubleFlag(2.0))

    app.appUiStateMachine.test(Unit) {
      awaitUntilBody<MoneyHomeBodyModel>(MONEY_HOME) {
        clickSettings()
      }

      awaitUntilBody<SettingsBodyModel>(
        SETTINGS,
        matching = { bodyModel ->
          bodyModel.sectionModels[1].rowModels.map { it.title }.contains("Private Wallet Update")
        }
      ) {
        clickPrivateWalletUpdateRow()
      }

      awaitUntilBody<PrivateWalletMigrationIntroBodyModel> {
        clickPrimaryButton()
      }

      awaitUntilSheet<PrivateWalletMigrationUtxoConsolidationRequiredSheetModel> {
        onContinue()
      }

      awaitUntilBody<LoadingSuccessBodyModel>(
        id = LOADING_UTXO_CONSOLIDATION_DETAILS
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitUntilBody<FormBodyModel>(id = UTXO_CONSOLIDATION_EXCEEDED_MAX_COUNT)
        .clickPrimaryButton()

      awaitUntilBody<UtxoConsolidationConfirmationModel> {
        clickPrimaryButton()
      }

      awaitSheet<TapAndHoldToConsolidateUtxosBodyModel> {
        clickPrimaryButton()
      }

      awaitUntilBody<LoadingSuccessBodyModel>(
        id = BROADCASTING_UTXO_CONSOLIDATION
      )

      awaitUntilBody<UtxoConsolidationTransactionSentModel> {
        clickPrimaryButton()
      }

      // Show the customer "You're fully consolidated"
      awaitUntilBody<FormBodyModel>(NOT_ENOUGH_UTXOS_TO_CONSOLIDATE) {
        clickPrimaryButton()
      }

      awaitUntilBody<PrivateWalletMigrationIntroBodyModel> {
        clickPrimaryButton()
      }

      // Here, the consolidation tx will still be pending. We show the warning here.
      awaitUntilSheet<PrivateWalletMigrationPendingTransactionsWarningSheetModel> {
        clickPrimaryButton()
      }

      cancelAndIgnoreRemainingEvents()
    }
  }
})

private suspend fun TestScope.launchLegacyAppReadyForMigration() =
  launchLegacyWalletApp().apply {
    onboardFullAccountWithFakeHardware()
    privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    balanceThresholdFeatureFlag.setFlagValue(FeatureFlagValue.DoubleFlag(-1.0))
  }
