package build.wallet.statemachine.auth

import app.cash.turbine.plusAssign
import bitkey.auth.AccessToken
import bitkey.auth.AccountAuthTokens
import bitkey.auth.RefreshToken
import bitkey.privilegedactions.ActionProofError
import bitkey.privilegedactions.ActionProofServiceFake
import build.wallet.analytics.events.screen.id.AuthEventTrackerScreenId
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.ActionProofHeader
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.f8e.auth.PrivilegedActionProof.HwKeyProof
import build.wallet.f8e.auth.PrivilegedActionProof.HwSignedAction
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.auth.ActionProofType.CancelLostAppRecovery
import build.wallet.statemachine.auth.ActionProofType.RotateSpendingKeyset
import build.wallet.statemachine.auth.ActionProofType.SetMobilePayLimit
import build.wallet.statemachine.auth.ActionProofType.UpdateDescriptorBackups
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Modal
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitUntilBody
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import uniffi.actionproof.Action

class HardwareAuthUiStateMachineImplTests : FunSpec({
  val actionProofService = ActionProofServiceFake()

  val refreshAuthTokensUiStateMachine = object :
    RefreshAuthTokensUiStateMachine,
    ScreenStateMachineMock<RefreshAuthTokensProps>("refresh-auth-tokens") {}

  val nfcSessionUIStateMachine = object :
    NfcSessionUIStateMachine,
    ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc-session") {}

  val nfcConfirmableSessionUiStateMachine = object :
    NfcConfirmableSessionUiStateMachine,
    ScreenStateMachineMock<NfcConfirmableSessionUIStateMachineProps<*>>("nfc-confirmable-session") {}

  val stateMachine = HardwareAuthUiStateMachineImpl(
    refreshAuthTokensUiStateMachine = refreshAuthTokensUiStateMachine,
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    nfcConfirmableSessionUiStateMachine = nfcConfirmableSessionUiStateMachine,
    actionProofService = actionProofService
  )

  val onSuccess = turbines.create<PrivilegedActionProof>("onSuccess")
  val onBack = turbines.create<Unit>("onBack")

  val segment = object : AppSegment {
    override val id: String = "test-segment"
  }

  val testTokens = AccountAuthTokens(
    accessToken = AccessToken("test-access-token"),
    refreshToken = RefreshToken("test-refresh-token"),
    accessTokenExpiresAt = null
  )

  beforeTest {
    actionProofService.reset()
  }

  context("W1 account") {
    val w1Props = HardwareAuthUiProps(
      fullAccountId = FullAccountMock.accountId,
      hardwareType = FullAccountMock.config.hardwareType,
      appAuthKey = FullAccountMock.keybox.activeAppKeyBundle.authKey,
      actionProofType = SetMobilePayLimit(limit = "100000", currency = "USD"),
      segment = segment,
      actionDescription = "Setting mobile pay limit",
      screenPresentationStyle = Modal,
      onSuccess = { onSuccess += it },
      onBack = { onBack += Unit }
    )

    test("W1: back from token refresh calls onBack") {
      stateMachine.test(w1Props) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onBack()
        }
      }

      onBack.awaitItem()
    }

    test("W1: back from NFC signing calls onBack") {
      stateMachine.test(w1Props) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBodyMock<NfcSessionUIStateMachineProps<String>>(id = "nfc-session") {
          onCancel()
        }
      }

      onBack.awaitItem()
    }

    test("W1: after token refresh, transitions to NFC access token signing and calls onSuccess") {
      stateMachine.test(w1Props) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBodyMock<NfcSessionUIStateMachineProps<String>>(id = "nfc-session") {
          onSuccess("signed-token")
        }
      }

      onSuccess.awaitItem().shouldBe(
        HwKeyProof(HwFactorProofOfPossession("signed-token"))
      )
    }
  }

  context("W3 account") {
    val w3Props = HardwareAuthUiProps(
      fullAccountId = FullAccountW3Mock.accountId,
      hardwareType = FullAccountW3Mock.config.hardwareType,
      appAuthKey = FullAccountW3Mock.keybox.activeAppKeyBundle.authKey,
      actionProofType = SetMobilePayLimit(limit = "100000", currency = "USD"),
      segment = segment,
      actionDescription = "Setting mobile pay limit",
      screenPresentationStyle = Modal,
      onSuccess = { onSuccess += it },
      onBack = { onBack += Unit }
    )

    test("W3: back from token refresh calls onBack") {
      stateMachine.test(w3Props) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onBack()
        }
      }

      onBack.awaitItem()
    }

    test("W3: back from NFC signing calls onBack") {
      stateMachine.test(w3Props) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBody<LoadingSuccessBodyModel>()
        awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<String>>(id = "nfc-confirmable-session") {
          onCancel()
        }
      }

      onBack.awaitItem()
    }

    test("W3: back from error screen calls onBack") {
      actionProofService.buildAppSignedPayloadResult =
        Err(ActionProofError.InternalError(RuntimeException("build failed")))

      stateMachine.test(w3Props) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBody<LoadingSuccessBodyModel>()
        awaitUntilBody<FormBodyModel> {
          this.onBack?.invoke()
        }
      }

      onBack.awaitItem()
    }

    test("W3: after token refresh, builds payload then transitions to NFC signing and calls onSuccess") {
      stateMachine.test(w3Props) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBody<LoadingSuccessBodyModel>()
        awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<String>>(id = "nfc-confirmable-session") {
          config.showNativeSheetOnIos.shouldBe(false)
          onSuccess("hw-signature")
        }
      }

      val call = actionProofService.buildAppSignedPayloadCalls.first()
      call.action.shouldBe(Action.SET_SPEND_WITHOUT_HARDWARE)
      call.value.shouldBe("100000")
      call.accountId.shouldBe(FullAccountW3Mock.accountId)

      onSuccess.awaitItem().shouldBe(
        HwSignedAction(
          actionProof = ActionProofHeader(
            version = 1,
            signatures = listOf("a".repeat(128), "hw-signature"),
            nonce = "a1"
          )
        )
      )
    }

    test("W3: shows error screen when payload build fails") {
      actionProofService.buildAppSignedPayloadResult =
        Err(ActionProofError.InternalError(RuntimeException("build failed")))

      stateMachine.test(w3Props) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBody<LoadingSuccessBodyModel>()
        awaitUntilBody<FormBodyModel> {
          header?.headline.shouldBe("We couldn’t verify this action")
        }
      }
    }

    test("W3: shows error screen when app signing fails") {
      actionProofService.buildAppSignedPayloadResult =
        Err(ActionProofError.InternalError(RuntimeException("signing failed")))

      stateMachine.test(w3Props) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBody<LoadingSuccessBodyModel>()
        awaitUntilBody<FormBodyModel> {
          header?.headline.shouldBe("We couldn’t verify this action")
        }
      }
    }

    test("W3: shows error screen when createActionProofHeader fails after NFC signing") {
      actionProofService.createActionProofHeaderResult =
        Err(ActionProofError.InternalError(RuntimeException("header creation failed")))

      stateMachine.test(w3Props) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBody<LoadingSuccessBodyModel>()
        awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<String>>(id = "nfc-confirmable-session") {
          onSuccess("hw-signature")
        }
        awaitUntilBody<FormBodyModel> {
          header?.headline.shouldBe("We couldn’t verify this action")
        }
      }
    }

    test("W3: UpdateDescriptorBackups builds the expected action-proof payload") {
      stateMachine.test(
        w3Props.copy(
          actionProofType = UpdateDescriptorBackups,
          actionDescription = "Updating descriptor backups"
        )
      ) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBody<LoadingSuccessBodyModel>()
        awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<String>>(id = "nfc-confirmable-session") {
          onSuccess("hw-signature")
        }
      }

      val call = actionProofService.buildAppSignedPayloadCalls.first()
      call.action.shouldBe(Action.UPDATE_DESCRIPTOR_BACKUPS)
      call.value.shouldBe(null)
      call.extra.shouldBe(emptyMap())
      onSuccess.awaitItem().shouldBeInstanceOf<HwSignedAction>()
    }

    test("W3: RotateSpendingKeyset includes the keyset id binding in the payload") {
      stateMachine.test(
        w3Props.copy(
          actionProofType = RotateSpendingKeyset(keysetId = "keyset-123"),
          actionDescription = "Rotating spending keyset"
        )
      ) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBody<LoadingSuccessBodyModel>()
        awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<String>>(id = "nfc-confirmable-session") {
          onSuccess("hw-signature")
        }
      }

      val call = actionProofService.buildAppSignedPayloadCalls.first()
      call.action.shouldBe(Action.ROTATE_SPENDING_KEYSET)
      call.value.shouldBe(null)
      call.extra.shouldBe(mapOf("eid" to "keyset-123"))
      onSuccess.awaitItem().shouldBeInstanceOf<HwSignedAction>()
    }
  }

  context("W3 account - hwSignatureOnly (CancelLostAppRecovery)") {
    val hwOnlyProps = HardwareAuthUiProps(
      fullAccountId = FullAccountW3Mock.accountId,
      hardwareType = FullAccountW3Mock.config.hardwareType,
      appAuthKey = FullAccountW3Mock.keybox.activeAppKeyBundle.authKey,
      actionProofType = CancelLostAppRecovery,
      segment = segment,
      actionDescription = "Canceling lost app recovery",
      screenPresentationStyle = Modal,
      onSuccess = { onSuccess += it },
      onBack = { onBack += Unit }
    )

    test("W3 hwSignatureOnly: skips app signing and includes only HW signature in header") {
      stateMachine.test(hwOnlyProps) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBody<LoadingSuccessBodyModel>()
        awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<String>>(id = "nfc-confirmable-session") {
          config.showNativeSheetOnIos.shouldBe(false)
          onSuccess("hw-signature")
        }
      }

      // Should NOT have called buildAppSignedPayload
      actionProofService.buildAppSignedPayloadCalls.shouldBe(emptyList())

      onSuccess.awaitItem().shouldBe(
        HwSignedAction(
          actionProof = ActionProofHeader(
            version = 1,
            signatures = listOf("hw-signature"),
            nonce = "a1"
          )
        )
      )
    }

    test("W3 hwSignatureOnly: shows error when buildBindings fails") {
      actionProofService.buildBindingsResult =
        Err(ActionProofError.InternalError(RuntimeException("bindings failed")))

      stateMachine.test(hwOnlyProps) {
        awaitBodyMock<RefreshAuthTokensProps>(id = "refresh-auth-tokens") {
          onSuccess(testTokens)
        }
        awaitBody<LoadingSuccessBodyModel>()

        awaitBody<FormBodyModel>(
          id = AuthEventTrackerScreenId.ACTION_PROOF_ERROR
        )
      }
    }
  }
})
