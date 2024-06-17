package build.wallet.statemachine.data.recovery.verification

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.EmailFake
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.notifications.NotificationTouchpointF8eClientMock
import build.wallet.f8e.recovery.RecoveryNotificationVerificationF8eClientMock
import build.wallet.ktor.result.HttpError
import build.wallet.notifications.NotificationTouchpoint
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.ChoosingNotificationTouchpointData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.EnteringVerificationCodeData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.LoadingNotificationTouchpointData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingNotificationTouchpointToServerData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingNotificationTouchpointToServerFailureData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingVerificationCodeToServerData
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationData.SendingVerificationCodeToServerFailureData
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf

class RecoveryNotificationVerificationDataStateMachineImplTests : FunSpec({

  val notificationTouchpointF8eClient = NotificationTouchpointF8eClientMock(turbines::create)
  val recoveryNotificationVerificationF8eClient =
    RecoveryNotificationVerificationF8eClientMock(
      turbine = turbines::create
    )

  val dataStateMachine =
    RecoveryNotificationVerificationDataStateMachineImpl(
      notificationTouchpointF8eClient = notificationTouchpointF8eClient,
      recoveryNotificationVerificationF8eClient = recoveryNotificationVerificationF8eClient
    )

  val propsOnCompleteCalls = turbines.create<Unit>("props onComplete calls")
  val propsOnRollbackCalls = turbines.create<Unit>("props onRollback calls")
  val props =
    RecoveryNotificationVerificationDataProps(
      f8eEnvironment = Production,
      fullAccountId = FullAccountId("account-id"),
      onRollback = { propsOnRollbackCalls.add(Unit) },
      onComplete = { propsOnCompleteCalls.add(Unit) },
      hwFactorProofOfPossession = null,
      lostFactor = PhysicalFactor.Hardware
    )

  beforeTest {
    notificationTouchpointF8eClient.reset()
    recoveryNotificationVerificationF8eClient.reset()
  }

  test("happy path") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointF8eClient.getTouchpointsResult = Ok(listOf(sms, email))
    recoveryNotificationVerificationF8eClient.sendCodeResult = Ok(Unit)
    recoveryNotificationVerificationF8eClient.verifyCodeResult = Ok(Unit)
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<LoadingNotificationTouchpointData>()
      notificationTouchpointF8eClient.getTouchpointsCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<ChoosingNotificationTouchpointData>()
        .onSmsClick.shouldNotBeNull().invoke()

      awaitItem().shouldBeInstanceOf<SendingNotificationTouchpointToServerData>()
      recoveryNotificationVerificationF8eClient.sendCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<EnteringVerificationCodeData>()
        .onCodeEntered("123")

      awaitItem().shouldBeInstanceOf<SendingVerificationCodeToServerData>()
      recoveryNotificationVerificationF8eClient.verifyCodeCalls.awaitItem()

      propsOnCompleteCalls.awaitItem()
    }
  }

  test("send code error - rollback and retry") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointF8eClient.getTouchpointsResult = Ok(listOf(sms, email))
    recoveryNotificationVerificationF8eClient.sendCodeResult = Err(HttpError.NetworkError(Throwable()))
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<LoadingNotificationTouchpointData>()
      notificationTouchpointF8eClient.getTouchpointsCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<ChoosingNotificationTouchpointData>()
        .onSmsClick.shouldNotBeNull().invoke()

      awaitItem().shouldBeInstanceOf<SendingNotificationTouchpointToServerData>()
      recoveryNotificationVerificationF8eClient.sendCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<SendingNotificationTouchpointToServerFailureData>()
        .rollback()

      awaitItem().shouldBeInstanceOf<ChoosingNotificationTouchpointData>()
        .onSmsClick.shouldNotBeNull().invoke()

      awaitItem().shouldBeInstanceOf<SendingNotificationTouchpointToServerData>()
      recoveryNotificationVerificationF8eClient.sendCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<SendingNotificationTouchpointToServerFailureData>()
        .retry()

      awaitItem().shouldBeInstanceOf<SendingNotificationTouchpointToServerData>()
      recoveryNotificationVerificationF8eClient.sendCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<SendingNotificationTouchpointToServerFailureData>()
    }
  }

  test("verify code error - rollback connectivity error") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointF8eClient.getTouchpointsResult = Ok(listOf(sms, email))
    recoveryNotificationVerificationF8eClient.sendCodeResult = Ok(Unit)
    recoveryNotificationVerificationF8eClient.verifyCodeResult =
      Err(F8eError.ConnectivityError(HttpError.NetworkError(Throwable())))
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<LoadingNotificationTouchpointData>()
      notificationTouchpointF8eClient.getTouchpointsCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<ChoosingNotificationTouchpointData>()
        .onSmsClick.shouldNotBeNull().invoke()

      awaitItem().shouldBeInstanceOf<SendingNotificationTouchpointToServerData>()
      recoveryNotificationVerificationF8eClient.sendCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<EnteringVerificationCodeData>()
        .onCodeEntered("123")

      awaitItem().shouldBeInstanceOf<SendingVerificationCodeToServerData>()
      recoveryNotificationVerificationF8eClient.verifyCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<SendingVerificationCodeToServerFailureData>()
        .rollback()

      awaitItem().shouldBeInstanceOf<EnteringVerificationCodeData>()
    }
  }

  test("verify code error - rollback non-connectivity error") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointF8eClient.getTouchpointsResult = Ok(listOf(sms, email))
    recoveryNotificationVerificationF8eClient.sendCodeResult = Ok(Unit)
    recoveryNotificationVerificationF8eClient.verifyCodeResult =
      Err(F8eError.UnhandledError(HttpError.NetworkError(Throwable())))
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<LoadingNotificationTouchpointData>()
      notificationTouchpointF8eClient.getTouchpointsCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<ChoosingNotificationTouchpointData>()
        .onSmsClick.shouldNotBeNull().invoke()

      awaitItem().shouldBeInstanceOf<SendingNotificationTouchpointToServerData>()
      recoveryNotificationVerificationF8eClient.sendCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<EnteringVerificationCodeData>()
        .onCodeEntered("123")

      awaitItem().shouldBeInstanceOf<SendingVerificationCodeToServerData>()
      recoveryNotificationVerificationF8eClient.verifyCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<SendingVerificationCodeToServerFailureData>()
        .rollback()

      awaitItem().shouldBeInstanceOf<ChoosingNotificationTouchpointData>()
    }
  }

  test("verify code error - retry") {
    val sms = NotificationTouchpoint.PhoneNumberTouchpoint(touchpointId = "sms", PhoneNumberMock)
    val email = NotificationTouchpoint.EmailTouchpoint(touchpointId = "email", EmailFake)
    notificationTouchpointF8eClient.getTouchpointsResult = Ok(listOf(sms, email))
    recoveryNotificationVerificationF8eClient.sendCodeResult = Ok(Unit)
    recoveryNotificationVerificationF8eClient.verifyCodeResult =
      Err(F8eError.UnhandledError(HttpError.NetworkError(Throwable())))
    dataStateMachine.test(props) {
      awaitItem().shouldBeInstanceOf<LoadingNotificationTouchpointData>()
      notificationTouchpointF8eClient.getTouchpointsCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<ChoosingNotificationTouchpointData>()
        .onSmsClick.shouldNotBeNull().invoke()

      awaitItem().shouldBeInstanceOf<SendingNotificationTouchpointToServerData>()
      recoveryNotificationVerificationF8eClient.sendCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<EnteringVerificationCodeData>()
        .onCodeEntered("123")

      awaitItem().shouldBeInstanceOf<SendingVerificationCodeToServerData>()
      recoveryNotificationVerificationF8eClient.verifyCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<SendingVerificationCodeToServerFailureData>()
        .retry()

      awaitItem().shouldBeInstanceOf<SendingVerificationCodeToServerData>()
      recoveryNotificationVerificationF8eClient.verifyCodeCalls.awaitItem()

      awaitItem().shouldBeInstanceOf<SendingVerificationCodeToServerFailureData>()
    }
  }
})
