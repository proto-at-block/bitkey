package build.wallet.integration.statemachine.recovery

import app.cash.turbine.test
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_BACKUP_FOUND
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.bitcoin.balance.BitcoinBalance.Companion.ZeroBalance
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.integration.statemachine.recovery.cloud.screenDecideIfShouldRotate
import build.wallet.money.BitcoinMoney
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.*
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.time.Duration.Companion.seconds

class LostAppRecoveryUsingCloudFunctionalTests : FunSpec({
  testForHardwareHappyPaths("recover keybox with no funds from cloud backup") { app, coverageMode ->
    app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    // copy cloud stores to new app, keep hardware
    val newApp = launchAppMatchingMode(
      referenceApp = app,
      cloudStoreAccountRepository = app.cloudStoreAccountRepository,
      cloudBackupStore = app.cloudBackupStore,
      hardwareSeed = app.fakeHardwareKeyStore.getSeed(),
      w3HardwareSeed = app.w3FakeHardwareKeyStore.getSeed()
    )

    newApp.appUiStateMachine.test(
      Unit,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<FormBodyModel>(CLOUD_BACKUP_FOUND)
        .clickPrimaryButton()
      if (coverageMode == HardwareCoverageMode.W3Private) {
        awaitUntilScreenWithBody<BodyModel>(
          matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
        ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
        awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
      }
      screenDecideIfShouldRotate {
        clickPrimaryButton()
      }
      val wallet = app.getActiveWallet()
      wallet.sync().unwrap()
      wallet.balance().test {
        awaitUntil { it == ZeroBalance }
        cancelAndIgnoreRemainingEvents()
      }

      awaitUntilBody<MoneyHomeBodyModel>(
        matching = { it.balanceModel.primaryAmount == "$0.00" }
      )

      cancelAndIgnoreRemainingEvents()
    }

    newApp.assertActiveHardwareType(coverageMode.hardwareType)
    newApp.verifyPostActivationState(
      PostActivationExpectations(
        expectedCanUseKeyboxKeysets = true,
        checkOnboardingArtifactsCleared = false
      )
    )
  }

  testForHardwareHappyPaths("recover keybox with some funds from cloud backup") { app, coverageMode ->
    app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )
    val treasury = app.treasuryWallet
    treasury.fund(app.getActiveWallet(), BitcoinMoney.sats(10_000))

    // copy cloud stores to new app, keep hardware
    val newApp = launchAppMatchingMode(
      referenceApp = app,
      cloudStoreAccountRepository = app.cloudStoreAccountRepository,
      cloudBackupStore = app.cloudBackupStore,
      hardwareSeed = app.fakeHardwareKeyStore.getSeed(),
      w3HardwareSeed = app.w3FakeHardwareKeyStore.getSeed()
    )

    newApp.appUiStateMachine.test(
      Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<FormBodyModel>(CLOUD_BACKUP_FOUND)
        .clickPrimaryButton()
      if (coverageMode == HardwareCoverageMode.W3Private) {
        awaitUntilScreenWithBody<BodyModel>(
          matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
        ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
        awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
      }
      screenDecideIfShouldRotate {
        clickPrimaryButton()
      }
      awaitUntilBody<MoneyHomeBodyModel>(
        matching = { it.balanceModel.primaryAmount == "$0.00" }
      )

      val wallet = app.getActiveWallet()
      wallet.sync().unwrap()

      wallet.balance().test {
        awaitUntil {
          it.total == BitcoinMoney.sats(10_000)
        }
        cancelAndIgnoreRemainingEvents()
      }

      // Spend sats
      app.returnFundsToTreasury()

      cancelAndIgnoreRemainingEvents()
    }

    newApp.assertActiveHardwareType(coverageMode.hardwareType)
    newApp.verifyPostActivationState(
      PostActivationExpectations(
        expectedCanUseKeyboxKeysets = true,
        checkOnboardingArtifactsCleared = false
      )
    )
  }

  testForHardwareHappyPaths("Cloud recovery, force exit app in middle of initiating") { app, coverageMode ->
    app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    // copy cloud stores to new app
    var newApp = launchAppMatchingMode(
      referenceApp = app,
      cloudStoreAccountRepository = app.cloudStoreAccountRepository,
      cloudBackupStore = app.cloudBackupStore
    )

    newApp.appUiStateMachine.test(
      Unit,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<FormBodyModel>(CLOUD_BACKUP_FOUND)
        .clickPrimaryButton()

      cancelAndIgnoreRemainingEvents()
    }

    // reset new app
    newApp = newApp.relaunchApp()

    newApp.appUiStateMachine.test(
      Unit,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
    }
  }

  testForHardwareHappyPaths("no cloud backup") { app, _ ->
    app.appUiStateMachine.test(Unit) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<FormBodyModel>(CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND)
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<FormBodyModel>(
        DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
      )
        .clickPrimaryButton()

      cancelAndIgnoreRemainingEvents()
    }
  }
})

private suspend fun TestScope.launchAppMatchingMode(
  referenceApp: AppTester,
  cloudStoreAccountRepository: build.wallet.cloud.store.CloudStoreAccountRepository? = null,
  cloudBackupStore: build.wallet.cloud.backup.CloudBackupStore? = null,
  hardwareSeed: build.wallet.nfc.FakeHardwareKeyStore.Seed? = null,
  w3HardwareSeed: build.wallet.nfc.FakeHardwareKeyStore.Seed? = null,
): AppTester {
  val app = if (referenceApp.appMode == AppMode.Private) {
    launchNewApp(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupStore = cloudBackupStore,
      hardwareSeed = hardwareSeed,
      w3HardwareSeed = w3HardwareSeed
    )
  } else {
    launchLegacyWalletApp(
      cloudStoreAccountRepository = cloudStoreAccountRepository,
      cloudBackupStore = cloudBackupStore,
      hardwareSeed = hardwareSeed,
      w3HardwareSeed = w3HardwareSeed
    )
  }

  referenceApp.accountConfigService.defaultConfig().value.hardwareType?.let {
    app.accountConfigService.setHardwareType(it).getOrThrow()
  }

  return app
}
