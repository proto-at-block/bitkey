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
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.getActiveWallet
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.returnFundsToTreasury
import com.github.michaelbull.result.unwrap
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class LostAppRecoveryUsingCloudFunctionalTests : FunSpec({
  lateinit var app: AppTester

  beforeTest {
    app = launchNewApp()
  }

  test("recover keybox with no funds from cloud backup") {
    app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake
    )

    // copy cloud stores to new app, keep hardware
    val newApp = launchNewApp(
      cloudStoreAccountRepository = app.app.cloudStoreAccountRepository,
      cloudKeyValueStore = app.app.cloudKeyValueStore,
      hardwareSeed = app.fakeHardwareKeyStore.getSeed()
    )

    newApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilScreenWithBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilScreenWithBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilScreenWithBody<FormBodyModel>(CLOUD_BACKUP_FOUND)
        .clickPrimaryButton()
      screenDecideIfShouldRotate {
        clickPrimaryButton()
      }
      val wallet = app.getActiveWallet()
      wallet.sync().unwrap()
      wallet.balance().test {
        awaitUntil { it == ZeroBalance }
        cancelAndIgnoreRemainingEvents()
      }

      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
        .balanceModel.primaryAmount.shouldBe("0 sats")

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("recover keybox with some funds from cloud backup") {
    val account =
      app.onboardFullAccountWithFakeHardware(
        cloudStoreAccountForBackup = CloudStoreAccount1Fake
      )
    val treasury = app.treasuryWallet
    treasury.fund(app.getActiveWallet(), BitcoinMoney.sats(10_000))

    // copy cloud stores to new app, keep hardware
    val newApp = launchNewApp(
      cloudStoreAccountRepository = app.app.cloudStoreAccountRepository,
      cloudKeyValueStore = app.app.cloudKeyValueStore,
      hardwareSeed = app.fakeHardwareKeyStore.getSeed()
    )

    newApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      testTimeout = 60.seconds,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilScreenWithBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilScreenWithBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilScreenWithBody<FormBodyModel>(CLOUD_BACKUP_FOUND)
        .clickPrimaryButton()
      screenDecideIfShouldRotate {
        clickPrimaryButton()
      }
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
        .balanceModel.primaryAmount.shouldBe("10,000 sats")

      val wallet = app.getActiveWallet()
      wallet.sync().unwrap()

      wallet.balance().test {
        awaitUntil {
          it.total == BitcoinMoney.sats(10_000)
        }
        cancelAndIgnoreRemainingEvents()
      }

      // Spend sats
      app.returnFundsToTreasury(account)

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Cloud recovery, force exit app in middle of initiating") {
    app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake
    )

    // copy cloud stores to new app
    var newApp = launchNewApp(
      cloudStoreAccountRepository = app.app.cloudStoreAccountRepository,
      cloudKeyValueStore = app.app.cloudKeyValueStore
    )

    newApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilScreenWithBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilScreenWithBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilScreenWithBody<FormBodyModel>(CLOUD_BACKUP_FOUND)
        .clickPrimaryButton()

      cancelAndIgnoreRemainingEvents()
    }

    // reset new app
    newApp = newApp.relaunchApp()

    newApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilScreenWithBody<ChooseAccountAccessModel>()
    }
  }

  test("no cloud backup") {
    app.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      awaitUntilScreenWithBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilScreenWithBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilScreenWithBody<FormBodyModel>(CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND)
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilScreenWithBody<FormBodyModel>(
        DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
      )
        .clickPrimaryButton()

      cancelAndIgnoreRemainingEvents()
    }
  }
})
