package build.wallet.statemachine.recovery.socrec.help

import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.bitkey.relationships.ProtectedCustomerFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.recovery.socrec.SocialChallengeError
import build.wallet.recovery.socrec.SocialChallengeVerifierMock
import build.wallet.relationships.RelationshipsCryptoFake
import build.wallet.relationships.RelationshipsKeysDaoFake
import build.wallet.relationships.RelationshipsKeysRepository
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.input.onValueChange
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.ui.awaitBody
import build.wallet.time.MinimumLoadingDuration
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.milliseconds

class HelpingWithRecoveryUiStateMachineImplTests : FunSpec({
  val onExitCalls = turbines.create<Unit>("on exit calls")

  val verifierMock = SocialChallengeVerifierMock()
  val relationshipsCrypto = RelationshipsCryptoFake()
  val stateMachine =
    HelpingWithRecoveryUiStateMachineImpl(
      socialChallengeVerifier = verifierMock,
      relationshipsKeysRepository = RelationshipsKeysRepository(
        relationshipsCrypto,
        RelationshipsKeysDaoFake()
      ),
      minimumLoadingDuration = MinimumLoadingDuration(0.milliseconds)
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
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        shouldClickVideoChat()
      }

      awaitBody<FormBodyModel> {
        shouldClickButtonWithTitle("Yes, I verified their identity")
      }

      awaitBody<FormBodyModel> {
        shouldHaveFieldModel().onValueChange("1234-56")
      }

      awaitBody<FormBodyModel> {
        shouldHaveFieldModel()
          .value
          .shouldBe("1234-56")

        primaryButton.shouldNotBeNull().let {
          it.isEnabled.shouldBeTrue()
          it.onClick.invoke()
        }
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Success>()
      }

      onExitCalls.awaitItem()
    }
  }

  test("Code Verification Failure") {
    verifierMock.error = SocialChallengeError.UnableToVerifyChallengeError(RuntimeException())

    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        shouldClickVideoChat()
      }

      awaitBody<FormBodyModel> {
        shouldClickButtonWithTitle("Yes, I verified their identity")
      }

      awaitBody<FormBodyModel> {
        shouldHaveFieldModel()
          .onValueChange("123456")
      }

      awaitBody<FormBodyModel> {
        shouldHaveFieldModel()
          .value
          .shouldBe("1234-56")

        primaryButton.shouldNotBeNull().let {
          it.isEnabled.shouldBeTrue()
          it.onClick.invoke()
        }
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
      }
      awaitBody<FormBodyModel>(
        SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_FAILURE
      ) {
        primaryButton.shouldNotBeNull().onClick.invoke()
      }
      awaitBody<FormBodyModel>(
        SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION
      )
    }
  }

  test("version mismatch") {
    verifierMock.error = SocialChallengeError.ChallengeCodeVersionMismatch(RuntimeException())

    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        shouldClickVideoChat()
      }

      awaitBody<FormBodyModel> {
        shouldClickButtonWithTitle("Yes, I verified their identity")
      }

      awaitBody<FormBodyModel> {
        shouldHaveFieldModel()
          .onValueChange("1234-56")
      }

      awaitBody<FormBodyModel> {
        shouldHaveFieldModel()
          .value
          .shouldBe("1234-56")

        primaryButton.shouldNotBeNull().let {
          it.isEnabled.shouldBeTrue()
          it.onClick.invoke()
        }
      }

      awaitBody<LoadingSuccessBodyModel> {
        state.shouldBeTypeOf<LoadingSuccessBodyModel.State.Loading>()
      }
      awaitBody<FormBodyModel>(
        SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION_FAILURE
      ) {
        header?.headline?.shouldBe("Bitkey app out of date")
        primaryButton.shouldNotBeNull().onClick.invoke()
      }
      awaitBody<FormBodyModel>(
        SocialRecoveryEventTrackerScreenId.TC_RECOVERY_CODE_VERIFICATION
      )
    }
  }

  test("selecting I'm not sure returns to get in touch screen") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        shouldClickVideoChat()

        awaitBody<FormBodyModel> {
          shouldClickButtonWithTitle("I'm not sure")
        }

        awaitBody<FormBodyModel> {
          id.shouldBe(SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH)
        }
      }
    }
  }

  test("security notice screen is shown when text message is selected") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        shouldClickTextMessage()
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Insecure verification method")

        onBack.shouldNotBeNull().invoke()
      }

      awaitBody<FormBodyModel> { // land back on first screen
        id.shouldBe(SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH)
      }
    }
  }

  test("security notice screen is shown when email is selected") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        shouldClickEmail()
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Insecure verification method")

        onBack.shouldNotBeNull().invoke()
      }

      awaitBody<FormBodyModel> { // land back on first screen
        id.shouldBe(SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH)
      }
    }
  }

  test("security notice screen is shown when phone call is selected") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        shouldClickPhoneCall()
      }

      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Insecure verification method")

        onBack.shouldNotBeNull().invoke()
      }

      awaitBody<FormBodyModel> { // land back on first screen
        id.shouldBe(SocialRecoveryEventTrackerScreenId.TC_RECOVERY_GET_IN_TOUCH)
      }
    }
  }

  test("onExit prop is called by state machine") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
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
