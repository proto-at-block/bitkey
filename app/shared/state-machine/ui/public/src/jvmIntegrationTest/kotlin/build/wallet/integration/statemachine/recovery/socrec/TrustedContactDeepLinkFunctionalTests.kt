package build.wallet.integration.statemachine.recovery.socrec

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId.BEING_TRUSTED_CONTACT_INTRODUCTION
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.cloud.store.CloudStoreAccountFake
import build.wallet.cloud.store.WritableCloudStoreAccountRepository
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.createTcInvite
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.onboardLiteAccountFromInvitation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TrustedContactDeepLinkFunctionalTests : FunSpec({

  beforeEach {
    Router.reset()
  }

  test("trusted contact deep link full account") {
    // full account creates invite
    val invite = launchNewApp()
    invite.onboardFullAccountWithFakeHardware()
    invite.app.trustedContactManagementUiStateMachine.test(
      props = buildTrustedContactManagementUiStateMachineProps(invite),
      useVirtualTime = false
    ) {
      advanceThroughTrustedContactInviteScreens("Bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = invite.getSharedInviteCode()

    // onboarded tester uses the invite
    val appTester = launchNewApp()
    appTester.onboardFullAccountWithFakeHardware()
    Router.route = Route.fromUrl("https://bitkey.world/links/downloads/trusted-contact#$inviteCode")
    appTester.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<FormBodyModel>(SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("trusted contact deep link lite account") {
    // full account creates invite
    val inviterApp = launchNewApp()
    inviterApp.onboardFullAccountWithFakeHardware()
    val tcInvite = inviterApp.createTcInvite("appTester")

    // tester onboard as TC
    val appTester = launchNewApp()
    appTester.onboardLiteAccountFromInvitation(tcInvite.inviteCode, "inviterApp")

    // generate another invite from another, different full account
    val secondInviter = launchNewApp()
    secondInviter.onboardFullAccountWithFakeHardware()
    secondInviter.app.trustedContactManagementUiStateMachine.test(
      props = buildTrustedContactManagementUiStateMachineProps(secondInviter),
      useVirtualTime = false
    ) {
      advanceThroughTrustedContactInviteScreens("Bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = secondInviter.getSharedInviteCode()

    // onboarded tester uses the invite
    Router.route = Route.fromUrl("https://bitkey.world/links/downloads/trusted-contact#$inviteCode")
    appTester.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<FormBodyModel>(SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("trusted contact deep link onboard new lite account instructions") {
    // full account creates invite
    val invite = launchNewApp()
    invite.onboardFullAccountWithFakeHardware()
    invite.app.trustedContactManagementUiStateMachine.test(
      props = buildTrustedContactManagementUiStateMachineProps(invite),
      useVirtualTime = false
    ) {
      advanceThroughTrustedContactInviteScreens("Bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = invite.getSharedInviteCode()

    // lite account onboards
    val appTester = launchNewApp()
    Router.route = Route.fromUrl("https://bitkey.world/links/downloads/trusted-contact#$inviteCode")
    appTester.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccountFake.CloudStoreAccount1Fake)
      awaitUntilScreenWithBody<FormBodyModel>(BEING_TRUSTED_CONTACT_INTRODUCTION) {
        clickPrimaryButton()
      }
      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilScreenWithBody<FormBodyModel>(SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("trusted contact deep link restore lite account") {
    // full account creates invite
    val invite = launchNewApp()
    invite.onboardFullAccountWithFakeHardware()
    invite.app.trustedContactManagementUiStateMachine.test(
      props = buildTrustedContactManagementUiStateMachineProps(invite),
      useVirtualTime = false
    ) {
      advanceThroughTrustedContactInviteScreens("Bob")
      cancelAndIgnoreRemainingEvents()
    }
    val inviteCode = invite.getSharedInviteCode()

    // lite account restores
    val appTester = launchNewApp()
    (appTester.app.cloudStoreAccountRepository as WritableCloudStoreAccountRepository)
      .set(CloudStoreAccountFake.TrustedContactFake)
    Router.route = Route.fromUrl("https://bitkey.world/links/downloads/trusted-contact#$inviteCode")
    appTester.app.appUiStateMachine.test(
      props = Unit,
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<CloudSignInModelFake>(CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccountFake.TrustedContactFake)
      awaitUntilScreenWithBody<FormBodyModel>(BEING_TRUSTED_CONTACT_INTRODUCTION) {
        clickPrimaryButton()
      }
      awaitUntilScreenWithBody<FormBodyModel>(SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_TC_ADD_CUSTOMER_NAME)
      cancelAndIgnoreRemainingEvents()
    }
  }
})
