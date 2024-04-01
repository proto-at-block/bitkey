package build.wallet.integration.statemachine.recovery.socrec.add

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.OutgoingInvitation
import build.wallet.bitkey.socrec.TrustedContactAlias
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Clock

class AddingTrustedContactFunctionalTests : FunSpec({
  val appTester = launchNewApp()
  val onExitCalls = turbines.create<Unit>("exit-tc-flow")
  val onInvitationShared = turbines.create<Unit>("add-tc")
  val onAddTcCalls = turbines.create<Unit>("tc-added")
  val onAddTc = { alias: TrustedContactAlias, _: HwFactorProofOfPossession ->
    onAddTcCalls.add(Unit)
    Ok(
      OutgoingInvitation(
        Invitation(
          "test-id",
          alias,
          "test-token",
          40,
          Clock.System.now()
        ),
        "test-token-123"
      )
    )
  }

  beforeAny {
    appTester.app.socialRecoveryServiceFake.reset()
  }

  test("Enter TC Name") {
    val account = appTester.onboardFullAccountWithFakeHardware()
    appTester.app.addingTcsUiStateMachine.test(
      AddingTrustedContactUiProps(
        account = account,
        onAddTc = onAddTc,
        onInvitationShared = { onInvitationShared.add(Unit) },
        onExit = { onExitCalls.add(Unit) }
      ),
      useVirtualTime = false
    ) {
      awaitUntilScreenWithBody<FormBodyModel>(
        id = SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME
      ) {
        header?.headline.shouldNotBeNull()
        header?.sublineModel.shouldNotBeNull()
        secondaryButton.shouldBe(null)
        primaryButton?.isEnabled.shouldBe(false)
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().run {
          fieldModel.value.shouldBeEmpty()
          fieldModel.onValueChange.invoke("tc-name", 0..6)
        }
      }
      awaitUntilScreenWithBody<FormBodyModel>(
        id = SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME,
        expectedBodyContentMatch = { it.primaryButton?.isEnabled == true }
      ) {
        clickPrimaryButton()
      }
      awaitUntilScreenWithBody<FormBodyModel> {
        onBack?.invoke()
      }
      awaitUntilScreenWithBody<FormBodyModel>(
        id = SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME,
        expectedBodyContentMatch = { it.primaryButton?.isEnabled == true }
      ) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().run {
          // Name field is retained:
          fieldModel.value.shouldBe("tc-name")
        }
      }
    }
  }

  test("Save Contact With Bitkey") {
    val account = appTester.onboardFullAccountWithFakeHardware()
    appTester.app.addingTcsUiStateMachine.test(
      AddingTrustedContactUiProps(
        account = account,
        onAddTc = onAddTc,
        onInvitationShared = { onInvitationShared.add(Unit) },
        onExit = { onExitCalls.add(Unit) }
      ),
      useVirtualTime = false
    ) {
      proceedWithFakeNames()
      awaitUntilScreenWithBody<FormBodyModel> {
        header?.headline.shouldBe("Save tc-name as a Trusted Contact")
      }
    }
  }

  test("Share Invite") {
    val account = appTester.onboardFullAccountWithFakeHardware()
    appTester.app.addingTcsUiStateMachine.test(
      AddingTrustedContactUiProps(
        account = account,
        onAddTc = onAddTc,
        onInvitationShared = { onInvitationShared.add(Unit) },
        onExit = { onExitCalls.add(Unit) }
      ),
      useVirtualTime = false
    ) {
      proceedWithFakeNames()
      awaitUntilScreenWithBody<FormBodyModel>().primaryButton?.onClick?.invoke()
      proceedNfcScreens()

      awaitUntilScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      onAddTcCalls.awaitItem()
      awaitUntilScreenWithBody<FormBodyModel> {
        header?.headline.shouldBe("Finally, invite tc-name to be your Trusted Contact")
        onBack?.invoke()
        // Pressing back after invite is created should finish the flow:
        onInvitationShared.awaitItem()
      }
    }
  }

  test("Complete TC Invite") {
    val account = appTester.onboardFullAccountWithFakeHardware()
    appTester.app.addingTcsUiStateMachine.test(
      AddingTrustedContactUiProps(
        account = account,
        onAddTc = onAddTc,
        onInvitationShared = { onInvitationShared.add(Unit) },
        onExit = { onExitCalls.add(Unit) }
      ),
      useVirtualTime = false
    ) {
      proceedWithFakeNames()
      awaitUntilScreenWithBody<FormBodyModel>().primaryButton?.onClick?.invoke()
      proceedNfcScreens()

      awaitUntilScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilScreenWithBody<FormBodyModel>().primaryButton?.onClick?.invoke()
      onAddTcCalls.awaitItem()
      awaitUntilScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().run {
          headline.shouldNotBeBlank()
          sublineModel.shouldNotBeNull().string.shouldNotBeBlank()
        }
        clickPrimaryButton()
      }
      onInvitationShared.awaitItem()
    }
  }
})
