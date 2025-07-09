package build.wallet.integration.statemachine.recovery.socrec.add

import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.relationships.Invitation
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.ui.awaitUntilBody
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
          setOf(TrustedContactRole.SocialRecoveryContact),
          "test-token",
          40,
          Clock.System.now()
        ),
        "test-token-123"
      )
    )
  }

  test("Enter TC Name") {
    val app = launchNewApp()
    val account = app.onboardFullAccountWithFakeHardware()
    app.addingTcsUiStateMachine.test(
      AddingTrustedContactUiProps(
        account = account,
        onAddTc = onAddTc,
        onInvitationShared = { onInvitationShared.add(Unit) },
        onExit = { onExitCalls.add(Unit) }
      )
    ) {
      awaitUntilBody<FormBodyModel>(
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
      awaitUntilBody<FormBodyModel>(
        id = SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME,
        matching = { it.primaryButton?.isEnabled == true }
      ) {
        clickPrimaryButton()
      }
      awaitUntilBody<FormBodyModel> {
        onBack?.invoke()
      }
      awaitUntilBody<FormBodyModel>(
        id = SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME,
        matching = { it.primaryButton?.isEnabled == true }
      ) {
        mainContentList.first().shouldBeTypeOf<FormMainContentModel.TextInput>().run {
          // Name field is retained:
          fieldModel.value.shouldBe("tc-name")
        }
      }
    }
  }

  test("Save Contact With Bitkey") {
    val app = launchNewApp()
    val account = app.onboardFullAccountWithFakeHardware()
    app.addingTcsUiStateMachine.test(
      AddingTrustedContactUiProps(
        account = account,
        onAddTc = onAddTc,
        onInvitationShared = { onInvitationShared.add(Unit) },
        onExit = { onExitCalls.add(Unit) }
      )
    ) {
      proceedWithFakeNames()
      awaitUntilBody<FormBodyModel> {
        header?.headline.shouldBe("Save tc-name as a Recovery Contact")
      }
    }
  }

  test("Share Invite") {
    val app = launchNewApp()
    val account = app.onboardFullAccountWithFakeHardware()
    app.addingTcsUiStateMachine.test(
      AddingTrustedContactUiProps(
        account = account,
        onAddTc = onAddTc,
        onInvitationShared = { onInvitationShared.add(Unit) },
        onExit = { onExitCalls.add(Unit) }
      )
    ) {
      proceedWithFakeNames()
      awaitUntilBody<FormBodyModel>().primaryButton?.onClick?.invoke()
      proceedNfcScreens()

      awaitUntilBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      onAddTcCalls.awaitItem()
      awaitUntilBody<FormBodyModel> {
        header?.headline.shouldBe("Finally, invite tc-name to be your Recovery Contact")
        onBack?.invoke()
        // Pressing back after invite is created should finish the flow:
        onInvitationShared.awaitItem()
      }
    }
  }

  test("Complete TC Invite") {
    val app = launchNewApp()
    val account = app.onboardFullAccountWithFakeHardware()
    app.addingTcsUiStateMachine.test(
      AddingTrustedContactUiProps(
        account = account,
        onAddTc = onAddTc,
        onInvitationShared = { onInvitationShared.add(Unit) },
        onExit = { onExitCalls.add(Unit) }
      )
    ) {
      proceedWithFakeNames()
      awaitUntilBody<FormBodyModel>().primaryButton?.onClick?.invoke()
      proceedNfcScreens()

      awaitUntilBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
      awaitUntilBody<FormBodyModel>().primaryButton?.onClick?.invoke()
      onAddTcCalls.awaitItem()
      awaitUntilBody<FormBodyModel> {
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
