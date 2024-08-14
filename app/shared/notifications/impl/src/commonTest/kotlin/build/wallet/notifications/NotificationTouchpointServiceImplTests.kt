@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.notifications

import app.cash.turbine.test
import build.wallet.account.AccountRepositoryFake
import build.wallet.account.AccountStatus
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.EmailFake
import build.wallet.f8e.notifications.NotificationTouchpointF8eClientMock
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.phonenumber.PhoneNumberMock
import com.github.michaelbull.result.Ok
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
  val accountRepository = AccountRepositoryFake()
  val service = NotificationTouchpointServiceImpl(
    notificationTouchpointF8eClient = notificationTouchpointF8eClient,
    notificationTouchpointDao = notificationTouchpointDao,
    accountRepository = accountRepository
  )

  beforeTest {
    accountRepository.reset()
    notificationTouchpointDao.reset()
    notificationTouchpointF8eClient.reset()
  }

  context("has active Full Account") {
    test("notificationTouchpointData updates when dao updates") {
      accountRepository.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
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
      accountRepository.accountState.value = Ok(AccountStatus.ActiveAccount(FullAccountMock))
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
      accountRepository.accountState.value = Ok(AccountStatus.NoAccount)

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
})
