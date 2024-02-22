package build.wallet.statemachine.data.notifications

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.EmailFake
import build.wallet.f8e.notifications.NotificationTouchpointServiceMock
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.notifications.NotificationTouchpointDaoMock
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.statemachine.core.test
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class NotificationTouchpointDataStateMachineImplTests : FunSpec({
  val notificationTouchpointDao = NotificationTouchpointDaoMock(turbines::create)
  val notificationTouchpointService = NotificationTouchpointServiceMock(turbines::create)
  val stateMachine =
    NotificationTouchpointDataStateMachineImpl(
      notificationTouchpointDao = notificationTouchpointDao,
      notificationTouchpointService = notificationTouchpointService
    )

  val props =
    NotificationTouchpointProps(
      account = FullAccountMock
    )

  beforeTest {
    notificationTouchpointDao.reset()
    notificationTouchpointService.reset()
  }

  test("initial state") {
    stateMachine.test(props) {
      notificationTouchpointService.getTouchpointsCalls.awaitItem()
      notificationTouchpointDao.clearCalls.awaitItem()

      with(awaitItem()) {
        phoneNumber.shouldBeNull()
        email.shouldBeNull()
      }
    }
  }

  test("updates when dao updates") {
    stateMachine.test(props) {
      notificationTouchpointService.getTouchpointsCalls.awaitItem()
      notificationTouchpointDao.clearCalls.awaitItem()

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
        phoneNumber.shouldNotBeNull()
        email.shouldNotBeNull()
      }
    }
  }

  test("refresh from f8e stores in dao") {
    notificationTouchpointService.getTouchpointsResult =
      Ok(
        listOf(
          EmailTouchpoint("email-id", EmailFake),
          PhoneNumberTouchpoint("sms-id", PhoneNumberMock)
        )
      )

    stateMachine.test(props) {
      notificationTouchpointService.getTouchpointsCalls.awaitItem()
      notificationTouchpointDao.clearCalls.awaitItem()
      notificationTouchpointDao.storeTouchpointCalls.awaitItem()
      notificationTouchpointDao.storeTouchpointCalls.awaitItem()

      // Initial state
      with(awaitItem()) {
        phoneNumber.shouldBeNull()
        email.shouldBeNull()
      }

      // After updating from server
      with(awaitItem()) {
        phoneNumber.shouldBe(PhoneNumberMock)
        email.shouldBe(EmailFake)
      }
    }
  }
})
