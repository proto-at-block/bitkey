@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.notifications

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.EmailFake
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.F8eError.UnhandledError
import build.wallet.f8e.error.code.VerifyTouchpointClientErrorCode
import build.wallet.f8e.notifications.NotificationTouchpointF8eClientMock
import build.wallet.f8e.recovery.RecoveryNotificationVerificationF8eClientMock
import build.wallet.f8e.recovery.RecoveryNotificationVerificationF8eClientMock.SendTouchpointCall
import build.wallet.f8e.recovery.RecoveryNotificationVerificationF8eClientMock.VerifyTouchpointCall
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.phonenumber.PhoneNumberMock
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

class NotificationTouchpointServiceImplTests : FunSpec({
  coroutineTestScope = true

  val notificationTouchpointDao = NotificationTouchpointDaoMock(turbines::create)
  val notificationTouchpointF8eClient = NotificationTouchpointF8eClientMock(turbines::create)
  val recoveryNotificationVerificationF8eClient =
    RecoveryNotificationVerificationF8eClientMock(turbine = turbines::create)
  val accountService = AccountServiceFake()
  val service = NotificationTouchpointServiceImpl(
    notificationTouchpointF8eClient = notificationTouchpointF8eClient,
    notificationTouchpointDao = notificationTouchpointDao,
    recoveryNotificationVerificationF8eClient = recoveryNotificationVerificationF8eClient,
    accountService = accountService
  )

  beforeTest {
    accountService.reset()
    notificationTouchpointDao.reset()
    notificationTouchpointF8eClient.reset()
    recoveryNotificationVerificationF8eClient.reset()
  }

  context("has active Full Account") {
    test("notificationTouchpointData updates when dao updates") {
      accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
      service.notificationTouchpointData().test {
        with(awaitItem()) {
          phoneNumber.shouldBeNull()
          email.shouldBeNull()
        }

        notificationTouchpointDao.phoneNumberFlow.value = PhoneNumberMock
        with(awaitItem()) {
          phoneNumber.shouldBe(PhoneNumberMock)
          email.shouldBeNull()
        }

        notificationTouchpointDao.emailFlow.value = EmailFake
        with(awaitItem()) {
          phoneNumber.shouldBe(PhoneNumberMock)
          email.shouldBe(EmailFake)
        }
      }
    }

    test("refresh from f8e stores in dao") {
      accountService.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
      notificationTouchpointF8eClient.getTouchpointsResult =
        Ok(
          listOf(
            EmailTouchpoint("email-id", EmailFake),
            PhoneNumberTouchpoint("sms-id", PhoneNumberMock)
          )
        )

      backgroundScope.launch {
        service.executeWork()
      }

      notificationTouchpointF8eClient.getTouchpointsCalls.awaitItem()
      notificationTouchpointDao.clearCalls.awaitItem()
      notificationTouchpointDao.storeTouchpointCalls.awaitItem()
      notificationTouchpointDao.storeTouchpointCalls.awaitItem()

      service.notificationTouchpointData().test {
        with(awaitItem()) {
          phoneNumber.shouldBe(PhoneNumberMock)
          email.shouldBe(EmailFake)
        }
      }
    }
  }

  context("no active account") {
    test("notificationTouchpointData is empty") {
      accountService.accountState.value = Ok(AccountStatus.NoAccount)

      backgroundScope.launch {
        service.executeWork()
      }

      service.notificationTouchpointData().test {
        with(awaitItem()) {
          phoneNumber.shouldBeNull()
          email.shouldBeNull()
        }
      }
    }
  }

  context("sync notification touchpoints") {
    test("success") {
      val result = Ok(
        listOf(
          EmailTouchpoint("email-id", EmailFake),
          PhoneNumberTouchpoint("sms-id", PhoneNumberMock)
        )
      )
      notificationTouchpointF8eClient.getTouchpointsResult = result

      val touchpointsResult =
        service.syncNotificationTouchpoints(FullAccountIdMock, F8eEnvironment.Staging)

      notificationTouchpointF8eClient.getTouchpointsCalls.awaitItem()
      notificationTouchpointDao.clearCalls.awaitItem()
      notificationTouchpointDao.storeTouchpointCalls.awaitItem()
      notificationTouchpointDao.storeTouchpointCalls.awaitItem()

      touchpointsResult.shouldBe(result)
    }

    test("error") {
      val result = Err(NetworkError(Throwable()))
      notificationTouchpointF8eClient.getTouchpointsResult = result

      val touchpointsResult =
        service.syncNotificationTouchpoints(FullAccountIdMock, F8eEnvironment.Staging)

      notificationTouchpointF8eClient.getTouchpointsCalls.awaitItem()
      notificationTouchpointDao.clearCalls.expectNoEvents()
      notificationTouchpointDao.storeTouchpointCalls.expectNoEvents()
      touchpointsResult.shouldBe(result)
    }
  }

  context("send verification code to touchpoint success") {
    test("success") {
      val touchpoint = EmailTouchpoint("id", EmailFake)
      service.sendVerificationCodeToTouchpoint(
        fullAccountId = FullAccountIdMock,
        f8eEnvironment = F8eEnvironment.Staging,
        touchpoint = touchpoint,
        hwProofOfPossession = HwFactorProofOfPossession("hw")
      )

      recoveryNotificationVerificationF8eClient.sendCodeCalls.awaitItem()
        .shouldBe(
          SendTouchpointCall(
            fullAccountId = FullAccountIdMock,
            touchpoint = touchpoint,
            hwFactorProofOfPossession = HwFactorProofOfPossession("hw")
          )
        )
    }

    test("error") {
      val result = Err(NetworkError(Throwable()))
      recoveryNotificationVerificationF8eClient.sendCodeResult = result

      val touchpoint = EmailTouchpoint("id", EmailFake)
      val errorResult = service.sendVerificationCodeToTouchpoint(
        fullAccountId = FullAccountIdMock,
        f8eEnvironment = F8eEnvironment.Staging,
        touchpoint = touchpoint,
        hwProofOfPossession = HwFactorProofOfPossession("hw")
      )

      errorResult.shouldBe(result)
      recoveryNotificationVerificationF8eClient.sendCodeCalls.awaitItem()
    }
  }

  context("verify code") {
    test("success") {
      service.verifyCode(
        fullAccountId = FullAccountIdMock,
        f8eEnvironment = F8eEnvironment.Staging,
        verificationCode = "verification-code",
        hwProofOfPossession = HwFactorProofOfPossession("hw")
      )

      recoveryNotificationVerificationF8eClient.verifyCodeCalls.awaitItem()
        .shouldBe(
          VerifyTouchpointCall(
            fullAccountId = FullAccountIdMock,
            verificationCode = "verification-code",
            hwFactorProofOfPossession = HwFactorProofOfPossession("hw")
          )
        )
    }

    test("error") {
      val result: Result<Unit, F8eError<VerifyTouchpointClientErrorCode>> =
        Err(UnhandledError(NetworkError(Throwable())))
      recoveryNotificationVerificationF8eClient.verifyCodeResult = result

      val errorResult = service.verifyCode(
        fullAccountId = FullAccountIdMock,
        f8eEnvironment = F8eEnvironment.Staging,
        verificationCode = "verification-code",
        hwProofOfPossession = HwFactorProofOfPossession("hw")
      )

      recoveryNotificationVerificationF8eClient.verifyCodeCalls.awaitItem()
      errorResult.shouldBe(result)
    }
  }
})
