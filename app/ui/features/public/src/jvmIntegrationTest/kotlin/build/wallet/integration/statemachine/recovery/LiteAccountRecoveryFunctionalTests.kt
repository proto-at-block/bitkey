package build.wallet.integration.statemachine.recovery

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.integration.statemachine.create.beTrustedContactButton
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.createTcInvite
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.onboardLiteAccountFromInvitation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.time.Duration.Companion.seconds

class LiteAccountRecoveryFunctionalTests : FunSpec({

  val protectedCustomerName = "kiki"
  val trustedContactName = "lala"

  test("lite account recovery via Be a Trusted Contact button") {

    // Protected Customer onboards and sends out an invite
    val protectedCustomerApp = launchNewApp()
    protectedCustomerApp.onboardFullAccountWithFakeHardware()
    val (inviteCode, _) =
      protectedCustomerApp.createTcInvite(
        tcName = trustedContactName
      )

    // Trusted Contact onboards by accepting Invitation
    val trustedContactApp = launchNewApp()
    trustedContactApp.onboardLiteAccountFromInvitation(
      inviteCode = inviteCode,
      protectedCustomerName = protectedCustomerName,
      cloudStoreAccountForBackup = CloudStoreAccountFake.CloudStoreAccount1Fake
    )

    // App is reinstalled and lite account is recovered
    val newApp = launchNewApp(
      cloudStoreAccountRepository = trustedContactApp.cloudStoreAccountRepository,
      cloudKeyValueStore = trustedContactApp.cloudKeyValueStore
    )
    newApp.appUiStateMachine.test(
      props = Unit,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .beTrustedContactButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>(
        CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
      )
        .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
      awaitUntilBody<LiteMoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("lite account recovery via Restore Existing Bitkey Wallet button") {

    // Protected Customer onboards and sends out an invite
    val protectedCustomerApp = launchNewApp()
    protectedCustomerApp.onboardFullAccountWithFakeHardware()
    val (inviteCode, _) =
      protectedCustomerApp.createTcInvite(
        tcName = trustedContactName
      )

    // Trusted Contact onboards by accepting Invitation
    val trustedContactApp = launchNewApp()
    trustedContactApp.onboardLiteAccountFromInvitation(
      inviteCode = inviteCode,
      protectedCustomerName = protectedCustomerName,
      cloudStoreAccountForBackup = CloudStoreAccountFake.CloudStoreAccount1Fake
    )

    // App is reinstalled and lite account is recovered
    val newApp = launchNewApp(
      cloudStoreAccountRepository = trustedContactApp.cloudStoreAccountRepository,
      cloudKeyValueStore = trustedContactApp.cloudKeyValueStore
    )
    newApp.appUiStateMachine.test(
      props = Unit,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>(
        CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
      )
        .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
      awaitUntilBody<LiteMoneyHomeBodyModel>()
    }
  }
})
