package build.wallet.integration.statemachine.recovery.socrec

import bitkey.ui.framework.test
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.WritableCloudStoreAccountRepository
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.account.BeTrustedContactIntroductionModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.trustedcontact.model.EnteringProtectedCustomerNameBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.createTcInvite
import build.wallet.testing.ext.getSharedInviteCode
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.onboardLiteAccountFromInvitation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TrustedContactDeepLinkFunctionalTests : FunSpec({

  beforeEach {
    Router.reset()
  }

  test("Recovery Contact deep link full account") {
    // full account creates invite
    val inviteApp = launchNewApp()
    inviteApp.onboardFullAccountWithFakeHardware()
    inviteApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(inviteApp)
    ) {
      advanceThroughTrustedContactInviteScreens("Bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = inviteApp.getSharedInviteCode()

    // onboarded tester uses the invite
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()
    Router.route = Route.from("https://bitkey.world/links/downloads/trusted-contact#$inviteCode")
    app.appUiStateMachine.test(
      props = Unit
    ) {
      awaitUntilBody<EnteringProtectedCustomerNameBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Recovery Contact deep link lite account") {
    // full account creates invite
    val inviterApp = launchNewApp()
    inviterApp.onboardFullAccountWithFakeHardware()
    val tcInvite = inviterApp.createTcInvite("app")

    // tester onboard as RC
    val app = launchNewApp()
    app.onboardLiteAccountFromInvitation(tcInvite.inviteCode, "inviterApp")

    // generate another invite from another, different full account
    val secondInviter = launchNewApp()
    secondInviter.onboardFullAccountWithFakeHardware()
    secondInviter.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(secondInviter)
    ) {
      advanceThroughTrustedContactInviteScreens("Bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = secondInviter.getSharedInviteCode()

    // onboarded tester uses the invite
    Router.route = Route.from("https://bitkey.world/links/downloads/trusted-contact#$inviteCode")
    app.appUiStateMachine.test(
      props = Unit
    ) {
      awaitUntilBody<EnteringProtectedCustomerNameBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Recovery Contact deep link onboard new lite account instructions") {
    // full account creates invite
    val inviteApp = launchNewApp()
    inviteApp.onboardFullAccountWithFakeHardware()
    inviteApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(inviteApp)
    ) {
      advanceThroughTrustedContactInviteScreens("Bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = inviteApp.getSharedInviteCode()

    // lite account onboards
    val app = launchNewApp()
    Router.route = Route.from("https://bitkey.world/links/downloads/trusted-contact#$inviteCode")
    app.appUiStateMachine.test(
      props = Unit
    ) {
      awaitUntilBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
      awaitUntilBody<BeTrustedContactIntroductionModel> {
        onContinue()
      }
      awaitUntilBody<LoadingSuccessBodyModel>(CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<EnteringProtectedCustomerNameBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Recovery Contact deep link restore lite account") {
    // full account creates invite
    val inviteApp = launchNewApp()
    inviteApp.onboardFullAccountWithFakeHardware()
    inviteApp.trustedContactManagementScreenPresenter.test(
      screen = buildTrustedContactManagementUiStateMachineProps(inviteApp)
    ) {
      advanceThroughTrustedContactInviteScreens("Bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = inviteApp.getSharedInviteCode()

    // lite account restores
    val app = launchNewApp()
    (app.cloudStoreAccountRepository as WritableCloudStoreAccountRepository)
      .set(CloudStoreAccountFake.TrustedContactFake)
    Router.route = Route.from("https://bitkey.world/links/downloads/trusted-contact#$inviteCode")
    app.appUiStateMachine.test(
      props = Unit
    ) {
      awaitUntilBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccountFake.TrustedContactFake)
      awaitUntilBody<BeTrustedContactIntroductionModel> {
        onContinue()
      }
      awaitUntilBody<EnteringProtectedCustomerNameBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }
})
