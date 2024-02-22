package build.wallet.statemachine.settings.full

import build.wallet.email.EmailFake
import build.wallet.notifications.NotificationTouchpointType.Email
import build.wallet.notifications.NotificationTouchpointType.PhoneNumber
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.Icon.TabIconHome
import build.wallet.statemachine.core.TabBarItem
import build.wallet.statemachine.core.TabBarModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.data.notifications.NotificationTouchpointData
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Settings
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiStateMachine
import build.wallet.statemachine.settings.full.notifications.NotificationsSettingsProps
import build.wallet.statemachine.settings.full.notifications.NotificationsSettingsUiStateMachineImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class NotificationsSettingsUiStateMachineImplTests : FunSpec({

  val stateMachine =
    NotificationsSettingsUiStateMachineImpl(
      notificationTouchpointInputAndVerificationUiStateMachine =
        object : NotificationTouchpointInputAndVerificationUiStateMachine,
          ScreenStateMachineMock<NotificationTouchpointInputAndVerificationProps>(
            id = "touchpoint"
          ) {}
    )

  val props =
    NotificationsSettingsProps(
      accountData = ActiveKeyboxLoadedDataMock,
      onBack = {}
    )

  test("initial state, no stored phone number") {
    stateMachine.test(props) {
      // Notification settings
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          with(listGroupModel.items[0]) {
            title.shouldBe("Text Messages")
            secondaryText.shouldBeNull()
          }
          with(listGroupModel.items[1]) {
            title.shouldBe("Emails")
            secondaryText.shouldBeNull()
          }
        }
      }
    }
  }

  test("initial state with stored phone number and email") {
    val notificationData = NotificationTouchpointData(PhoneNumberMock, EmailFake)
    val keyboxData = ActiveKeyboxLoadedDataMock.copy(notificationTouchpointData = notificationData)
    stateMachine.test(props.copy(accountData = keyboxData)) {
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          with(listGroupModel.items[0]) {
            title.shouldBe("Text Messages")
            secondaryText.shouldBe(PhoneNumberMock.formattedDisplayValue)
          }
          with(listGroupModel.items[1]) {
            title.shouldBe("Emails")
            secondaryText.shouldBe(EmailFake.value)
          }
        }
      }
    }
  }

  test("open and close phone number flow") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        // Tap on sms row
        mainContentList.first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items[0]
          .onClick.shouldNotBeNull().invoke()
      }

      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(PhoneNumber)
        entryPoint.shouldBeInstanceOf<Settings>()
        onClose()
      }

      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("open and close email flow") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        // Tap on email row
        mainContentList.first()
          .shouldBeInstanceOf<ListGroup>()
          .listGroupModel
          .items[1]
          .onClick.shouldNotBeNull().invoke()
      }

      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(Email)
        entryPoint.shouldBeInstanceOf<Settings>()
        onClose()
      }

      awaitScreenWithBody<FormBodyModel>()
    }
  }
})

val TabBarItemMock =
  TabBarItem(
    icon = TabIconHome,
    selected = false,
    onClick = {}
  )
val TabBarModelMock =
  TabBarModel(
    isShown = false,
    firstItem = TabBarItemMock,
    secondItem = TabBarItemMock
  )
