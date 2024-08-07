package build.wallet.statemachine.recovery.socrec.help

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.socrec.ProtectedCustomerFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.recovery.socrec.*
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.input.onValueChange
import build.wallet.statemachine.core.test
import build.wallet.time.ControlledDelayer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class HelpingWithRecoveryUiStateMachineImplTests : FunSpec({
  val onExitCalls = turbines.create<Unit>("on exit calls")

  val verifierMock = SocialChallengeVerifierMock()
  val socRecCrypto = SocRecCryptoFake()
  val stateMachine =
    HelpingWithRecoveryUiStateMachineImpl(
      delayer = ControlledDelayer(),
      socialChallengeVerifier = verifierMock,
      socRecKeysRepository = SocRecKeysRepository(socRecCrypto, SocRecKeysDaoFake())
    )

  val props =
    HelpingWithRecoveryUiProps(
      onExit = { onExitCalls += Unit },
      protectedCustomer = ProtectedCustomerFake,
      account = LiteAccountMock
    )

  beforeAny {
    verifierMock.clear()
  }

  test("successful completion of the flow") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        shouldClickVideoChat()
      }

      awaitScreenWithBody<FormBodyModel> {
        shouldClickButtonWithTitle("Yes, I verified their identity")
      }

      awaitScreenWithBody<FormBodyModel> {
        shouldHaveFieldModel().onValueChange("1234-56")
      }

      awaitScreenWithBody<FormBodyModel> {
        shouldHaveFieldModel()
          .value
          .shouldBe("1234-56")

        primaryButton.shouldNotBeNull().let {
          it.isEnabled.shouldBeTrue()
          it.onClick.invoke()
        }
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Success>()
      }

      onExitCalls.awaitItem()
    }
  }

  test("Code Verification Failure") {
    verifierMock.error = SocialChallengeError.UnableToVerifyChallengeError(RuntimeException())

    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        shouldClickVideoChat()
      }

      awaitScreenWithBody<FormBodyModel> {
        shouldClickButtonWithTitle("Yes, I verified their identity")
      }

      awaitScreenWithBody<FormBodyModel> {
        shouldHaveFieldModel()
          .onValueChange("123456")
      }

      awaitScreenWithBody<FormBodyModel> {
        shouldHaveFieldModel()
          .value
          .shouldBe("1234-56")

        primaryButton.shouldNotBeNull().let {
          it.isEnabled.shouldBeTrue()
          it.onClick.invoke()
        }
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
      }
      awaitScreenWithBody<FormBodyModel>(
        SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_FAILURE
      ) {
        primaryButton.shouldNotBeNull().onClick.invoke()
      }
      awaitScreenWithBody<FormBodyModel>(
        SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION
      )
    }
  }

  test("version mismatch") {
    verifierMock.error = SocialChallengeError.ChallengeCodeVersionMismatch(RuntimeException())

    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        shouldClickVideoChat()
      }

      awaitScreenWithBody<FormBodyModel> {
        shouldClickButtonWithTitle("Yes, I verified their identity")
      }

      awaitScreenWithBody<FormBodyModel> {
        shouldHaveFieldModel()
          .onValueChange("1234-56")
      }

      awaitScreenWithBody<FormBodyModel> {
        shouldHaveFieldModel()
          .value
          .shouldBe("1234-56")

        primaryButton.shouldNotBeNull().let {
          it.isEnabled.shouldBeTrue()
          it.onClick.invoke()
        }
      }

      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
      }
      awaitScreenWithBody<FormBodyModel>(
        SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_FAILURE
      ) {
        header?.headline?.shouldBe("Bitkey app out of date")
        primaryButton.shouldNotBeNull().onClick.invoke()
      }
      awaitScreenWithBody<FormBodyModel>(
        SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION
      )
    }
  }

  test("selecting I'm not sure returns to get in touch screen") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        shouldClickVideoChat()

        awaitScreenWithBody<FormBodyModel> {
          shouldClickButtonWithTitle("I'm not sure")
        }

        awaitScreenWithBody<FormBodyModel> {
          id.shouldBe(SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH)
        }
      }
    }
  }

  test("security notice screen is shown when text message is selected") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        shouldClickTextMessage()
      }

      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Insecure verification method")

        onBack.shouldNotBeNull().invoke()
      }

      awaitScreenWithBody<FormBodyModel> { // land back on first screen
        id.shouldBe(SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH)
      }
    }
  }

  test("security notice screen is shown when email is selected") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        shouldClickEmail()
      }

      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Insecure verification method")

        onBack.shouldNotBeNull().invoke()
      }

      awaitScreenWithBody<FormBodyModel> { // land back on first screen
        id.shouldBe(SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH)
      }
    }
  }

  test("security notice screen is shown when phone call is selected") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        shouldClickPhoneCall()
      }

      awaitScreenWithBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Insecure verification method")

        onBack.shouldNotBeNull().invoke()
      }

      awaitScreenWithBody<FormBodyModel> { // land back on first screen
        id.shouldBe(SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH)
      }
    }
  }

  test("onExit prop is called by state machine") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        toolbar
          .shouldNotBeNull()
          .leadingAccessory
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()
          .model
          .onClick()
      }

      onExitCalls.awaitItem()
    }
  }
})

private fun FormBodyModel.shouldHaveFieldModel() =
  mainContentList
    .first()
    .shouldBeInstanceOf<FormMainContentModel.TextInput>()
    .fieldModel

private fun FormBodyModel.shouldClickTextMessage() = shouldClickButtonWithTitle("Text Message")

private fun FormBodyModel.shouldClickEmail() = shouldClickButtonWithTitle("Email")

private fun FormBodyModel.shouldClickPhoneCall() = shouldClickButtonWithTitle("Phone Call")

private fun FormBodyModel.shouldClickVideoChat() = shouldClickButtonWithTitle("Video Chat")

private fun FormBodyModel.shouldClickButtonWithTitle(text: String) {
  mainContentList
    .first()
    .shouldBeInstanceOf<FormMainContentModel.ListGroup>()
    .listGroupModel
    .items
    .first { it.title == text }
    .onClick
    .shouldNotBeNull()
    .invoke()
}
