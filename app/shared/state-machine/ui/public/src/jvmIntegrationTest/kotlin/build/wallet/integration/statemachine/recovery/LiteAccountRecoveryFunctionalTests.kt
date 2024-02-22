package build.wallet.integration.statemachine.recovery

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.cloudServiceProvider
import build.wallet.integration.statemachine.create.beTrustedContactButton
import build.wallet.integration.statemachine.create.moreOptionsButton
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.AppTester
import build.wallet.testing.launchNewApp
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.time.Duration.Companion.seconds

class LiteAccountRecoveryFunctionalTests : FunSpec({

  val protectedCustomerName = "kiki"
  val trustedContactName = "lala"

  test("lite account recovery via Be a Trusted Contact button") {

    // Protected Customer onboards and sends out an invite
    val protectedCustomerApp = launchNewApp()
    val customerAccount = protectedCustomerApp.onboardFullAccountWithFakeHardware()
    val tcInvitation =
      protectedCustomerApp.createTcInvite(
        account = customerAccount,
        tcName = trustedContactName
      )

    // Trusted Contact onboards by accepting Invitation
    val trustedContactApp = launchNewApp()
    trustedContactApp.deleteCloudBackup()
    trustedContactApp.onboardLiteAccountFromInvitation(
      invitation = tcInvitation,
      protectedCustomerName = protectedCustomerName,
      cloudStoreAccountForBackup = CloudStoreAccountFake.CloudStoreAccount1Fake
    )

    // App is reinstalled and lite account is recovered
    val newApp = launchNewApp(
      cloudStoreAccountRepository = trustedContactApp.app.cloudStoreAccountRepository,
      cloudKeyValueStore = trustedContactApp.app.cloudKeyValueStore
    )
    newApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilScreenWithBody<ChooseAccountAccessModel>()
        .moreOptionsButton.onClick()
      awaitUntilScreenWithBody<FormBodyModel>()
        .beTrustedContactButton.onClick.shouldNotBeNull().invoke()
      awaitUntilScreenWithBody<FormBodyModel>()
        .primaryButton.shouldNotBeNull().onClick()
      awaitUntilScreenWithBody<CloudSignInModelFake>(
        CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
      )
        .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
    }
  }

  test("lite account recovery via Restore Existing Bitkey Wallet button") {

    // Protected Customer onboards and sends out an invite
    val protectedCustomerApp = launchNewApp()
    val customerAccount = protectedCustomerApp.onboardFullAccountWithFakeHardware()
    val tcInvitation =
      protectedCustomerApp.createTcInvite(
        account = customerAccount,
        tcName = trustedContactName
      )

    // Trusted Contact onboards by accepting Invitation
    val trustedContactApp = launchNewApp()
    trustedContactApp.deleteCloudBackup()
    trustedContactApp.onboardLiteAccountFromInvitation(
      invitation = tcInvitation,
      protectedCustomerName = protectedCustomerName,
      cloudStoreAccountForBackup = CloudStoreAccountFake.CloudStoreAccount1Fake
    )

    // App is reinstalled and lite account is recovered
    val newApp = launchNewApp(
      cloudStoreAccountRepository = trustedContactApp.app.cloudStoreAccountRepository,
      cloudKeyValueStore = trustedContactApp.app.cloudKeyValueStore
    )
    newApp.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false,
      turbineTimeout = 10.seconds
    ) {
      awaitUntilScreenWithBody<ChooseAccountAccessModel>()
        .moreOptionsButton.onClick()
      awaitUntilScreenWithBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilScreenWithBody<CloudSignInModelFake>(
        CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
      )
        .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
    }
  }
})

private suspend fun AppTester.deleteCloudBackup() {
  app.cloudBackupDeleter.delete(cloudServiceProvider())
  deleteBackupsFromFakeCloud()
}
