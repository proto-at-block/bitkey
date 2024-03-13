package build.wallet.statemachine.recovery.verification

import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.error.F8eError
import build.wallet.ktor.result.HttpError
import build.wallet.notifications.NotificationTouchpoint
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.input.VerificationCodeInputProps
import build.wallet.statemachine.core.input.VerificationCodeInputStateMachine
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.ChoosingNotificationTouchpointData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.EnteringVerificationCodeData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.LoadingNotificationTouchpointData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.LoadingNotificationTouchpointFailureData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingNotificationTouchpointToServerData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingNotificationTouchpointToServerFailureData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingVerificationCodeToServerData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingVerificationCodeToServerFailureData
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class RecoveryNotificationVerificationUiStateMachineImplTests : FunSpec({

  val stateMachine =
    RecoveryNotificationVerificationUiStateMachineImpl(
      verificationCodeInputStateMachine =
        object : VerificationCodeInputStateMachine, ScreenStateMachineMock<VerificationCodeInputProps>(
          "verification-code-input"
        ) {}
    )

  val loadingNotificationTouchpointDataProps = LoadingNotificationTouchpointData.toProps()

  val loadingNotificationTouchpointFailureDataPropsRetryCalls =
    turbines.create<Unit>(
      "LoadingNotificationTouchpointFailureData retry calls"
    )
  val loadingNotificationTouchpointFailureDataPropsRollbackCalls =
    turbines.create<Unit>(
      "LoadingNotificationTouchpointFailureData rollback calls"
    )
  val loadingNotificationTouchpointFailureDataProps =
    LoadingNotificationTouchpointFailureData(
      error = HttpError.NetworkError(Throwable()),
      retry = { loadingNotificationTouchpointFailureDataPropsRetryCalls.add(Unit) },
      rollback = { loadingNotificationTouchpointFailureDataPropsRollbackCalls.add(Unit) }
    ).toProps()

  val choosingNotificationTouchpointDataPropsRollbackCalls =
    turbines.create<Unit>(
      "ChoosingNotificationTouchpointData rollback calls"
    )
  val choosingNotificationTouchpointDataPropsSmsClickCalls =
    turbines.create<Unit>(
      "ChoosingNotificationTouchpointData onSmsClick calls"
    )
  val choosingNotificationTouchpointDataPropsEmailClickCalls =
    turbines.create<Unit>(
      "ChoosingNotificationTouchpointData onEmailClick calls"
    )
  val choosingNotificationTouchpointDataProps =
    ChoosingNotificationTouchpointData(
      rollback = { choosingNotificationTouchpointDataPropsRollbackCalls.add(Unit) },
      onSmsClick = { choosingNotificationTouchpointDataPropsSmsClickCalls.add(Unit) },
      onEmailClick = { choosingNotificationTouchpointDataPropsEmailClickCalls.add(Unit) }
    ).toProps()

  val sendingNotificationTouchpointToServerDataProps =
    SendingNotificationTouchpointToServerData
      .toProps()

  val sendingNotificationTouchpointToServerFailureDataPropsRetryCalls =
    turbines.create<Unit>(
      "SendingNotificationTouchpointToServerFailureData retry calls"
    )
  val sendingNotificationTouchpointToServerFailureDataPropsRollbackCalls =
    turbines.create<Unit>(
      "SendingNotificationTouchpointToServerFailureData rollback calls"
    )
  val sendingNotificationTouchpointToServerFailureDataProps =
    SendingNotificationTouchpointToServerFailureData(
      error = HttpError.NetworkError(Throwable()),
      retry = { sendingNotificationTouchpointToServerFailureDataPropsRetryCalls.add(Unit) },
      rollback = { sendingNotificationTouchpointToServerFailureDataPropsRollbackCalls.add(Unit) }
    ).toProps()

  val enteringVerificationCodeDataPropsRollbackCalls =
    turbines.create<Unit>(
      "EnteringVerificationCodeData rollback calls"
    )
  val enteringVerificationCodeDataPropsCodeEnteredCalls =
    turbines.create<String>(
      "EnteringVerificationCodeData onCodeEntered calls"
    )
  val enteringVerificationCodeDataProps =
    EnteringVerificationCodeData(
      rollback = { enteringVerificationCodeDataPropsRollbackCalls.add(Unit) },
      touchpoint = NotificationTouchpoint.PhoneNumberTouchpoint("", PhoneNumberMock),
      onResendCode = { _, _ -> },
      onCodeEntered = { enteringVerificationCodeDataPropsCodeEnteredCalls.add(it) },
      lostFactor = PhysicalFactor.Hardware
    ).toProps()

  val sendingVerificationCodeToServerDataProps = SendingVerificationCodeToServerData.toProps()

  val sendingVerificationCodeToServerFailureDataPropsRetryCalls =
    turbines.create<Unit>(
      "SendingVerificationCodeToServerFailureData retry calls"
    )
  val sendingVerificationCodeToServerFailureDataPropsRollbackCalls =
    turbines.create<Unit>(
      "SendingVerificationCodeToServerFailureData rollback calls"
    )
  val sendingVerificationCodeToServerFailureDataProps =
    SendingVerificationCodeToServerFailureData(
      error = F8eError.ConnectivityError(HttpError.NetworkError(Throwable())),
      retry = { sendingVerificationCodeToServerFailureDataPropsRetryCalls.add(Unit) },
      rollback = { sendingVerificationCodeToServerFailureDataPropsRollbackCalls.add(Unit) }
    ).toProps()

  test("LoadingNotificationTouchpointData model") {
    stateMachine.test(loadingNotificationTouchpointDataProps) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("LoadingNotificationTouchpointFailureData model") {
    stateMachine.test(loadingNotificationTouchpointFailureDataProps) {
      awaitScreenWithBody<FormBodyModel> {
        onBack.shouldNotBeNull().invoke()
        loadingNotificationTouchpointFailureDataPropsRollbackCalls.awaitItem()

        clickPrimaryButton()
        loadingNotificationTouchpointFailureDataPropsRetryCalls.awaitItem()

        clickSecondaryButton()
        loadingNotificationTouchpointFailureDataPropsRollbackCalls.awaitItem()
      }
    }
  }

  test("ChoosingNotificationTouchpointData model") {
    stateMachine.test(choosingNotificationTouchpointDataProps) {
      awaitScreenWithBody<FormBodyModel> {
        onBack.shouldNotBeNull().invoke()
        choosingNotificationTouchpointDataPropsRollbackCalls.awaitItem()

        val listModel =
          mainContentList.first()
            .shouldBeInstanceOf<FormMainContentModel.ListGroup>().listGroupModel

        listModel.items[0].onClick.shouldNotBeNull().invoke() // Sms
        choosingNotificationTouchpointDataPropsSmsClickCalls.awaitItem()

        listModel.items[1].onClick.shouldNotBeNull().invoke() // Email
        choosingNotificationTouchpointDataPropsEmailClickCalls.awaitItem()
      }
    }
  }

  test("SendingNotificationTouchpointToServerData model") {
    stateMachine.test(sendingNotificationTouchpointToServerDataProps) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("SendingNotificationTouchpointToServerFailureData model") {
    stateMachine.test(sendingNotificationTouchpointToServerFailureDataProps) {
      awaitScreenWithBody<FormBodyModel> {
        onBack.shouldNotBeNull().invoke()
        sendingNotificationTouchpointToServerFailureDataPropsRollbackCalls.awaitItem()

        clickPrimaryButton()
        sendingNotificationTouchpointToServerFailureDataPropsRetryCalls.awaitItem()

        clickSecondaryButton()
        sendingNotificationTouchpointToServerFailureDataPropsRollbackCalls.awaitItem()
      }
    }
  }

  test("EnteringVerificationCodeData model") {
    stateMachine.test(enteringVerificationCodeDataProps) {
      awaitScreenWithBodyModelMock<VerificationCodeInputProps> {
        onBack.shouldNotBeNull().invoke()
        enteringVerificationCodeDataPropsRollbackCalls.awaitItem()

        onCodeEntered.invoke("code")
        enteringVerificationCodeDataPropsCodeEnteredCalls.awaitItem().shouldBe("code")
      }
    }
  }

  test("SendingVerificationCodeToServerData model") {
    stateMachine.test(sendingVerificationCodeToServerDataProps) {
      awaitScreenWithBody<LoadingSuccessBodyModel> {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }
    }
  }

  test("SendingVerificationCodeToServerFailureData model") {
    stateMachine.test(sendingVerificationCodeToServerFailureDataProps) {
      awaitScreenWithBody<FormBodyModel> {
        onBack.shouldNotBeNull().invoke()
        sendingVerificationCodeToServerFailureDataPropsRollbackCalls.awaitItem()

        clickPrimaryButton()
        sendingVerificationCodeToServerFailureDataPropsRetryCalls.awaitItem()

        clickSecondaryButton()
        sendingVerificationCodeToServerFailureDataPropsRollbackCalls.awaitItem()
      }
    }
  }
})

private fun RecoveryNotificationVerificationData.toProps() =
  RecoveryNotificationVerificationUiProps(
    recoveryNotificationVerificationData = this,
    lostFactor = PhysicalFactor.Hardware
  )
