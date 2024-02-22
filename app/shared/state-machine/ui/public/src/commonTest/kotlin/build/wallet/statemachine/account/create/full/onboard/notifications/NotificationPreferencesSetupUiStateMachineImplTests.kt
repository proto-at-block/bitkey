package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_EMAIL_INPUT_SKIP
import build.wallet.analytics.v1.Action.ACTION_APP_PHONE_NUMBER_INPUT_SKIP
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_ENABLED
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyboxConfigMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.EmailFake
import build.wallet.notifications.NotificationTouchpointDaoMock
import build.wallet.notifications.NotificationTouchpointType.Email
import build.wallet.notifications.NotificationTouchpointType.PhoneNumber
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.onboarding.OnboardingKeyboxStepState
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoMock
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.NeedsAction
import build.wallet.statemachine.account.notifications.NotificationPermissionRequesterMock
import build.wallet.statemachine.core.Icon.SmallIconXFilled
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Onboarding
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiStateMachine
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.icon.IconTint.On60
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemTreatment.SECONDARY
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

typealias SkipBottomSheetProvider = ((onBack: () -> Unit) -> SheetModel)

class NotificationPreferencesSetupUiStateMachineImplTests : FunSpec({

  val onCompleteCalls = turbines.create<Unit>("on complete calls")

  val skipSheetOnBackCalls = turbines.create<Unit>("skip on back calls")

  val eventTracker = EventTrackerMock(turbines::create)
  val onboardingKeyboxStepStateDao =
    OnboardingKeyboxStepStateDaoMock(turbines::create)
  val notificationTouchpointDao = NotificationTouchpointDaoMock(turbines::create)
  val notificationPermissionRequester = NotificationPermissionRequesterMock(turbines::create)
  val pushItemModelProvider = NotificationPreferencesSetupPushItemModelProviderMock()

  val stateMachine =
    NotificationPreferencesSetupUiStateMachineImpl(
      eventTracker = eventTracker,
      notificationPermissionRequester = notificationPermissionRequester,
      notificationTouchpointDao = notificationTouchpointDao,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      notificationTouchpointInputAndVerificationUiStateMachine =
        object : NotificationTouchpointInputAndVerificationUiStateMachine,
          ScreenStateMachineMock<NotificationTouchpointInputAndVerificationProps>(
            id = "touchpoint"
          ) {},
      pushItemModelProvider = pushItemModelProvider
    )

  val props =
    NotificationPreferencesSetupUiProps(
      fullAccountId = FullAccountIdMock,
      keyboxConfig = KeyboxConfigMock,
      onComplete = { onCompleteCalls.add(Unit) }
    )

  beforeTest {
    notificationTouchpointDao.reset()
    onboardingKeyboxStepStateDao.reset()
    notificationPermissionRequester.reset()
  }

  // Helper function
  fun SkipBottomSheetProvider.invoke() = this.invoke { skipSheetOnBackCalls.add(Unit) }

  test("setup shows push, sms and email") {
    stateMachine.test(props) {
      // Setup instructions
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          // Push
          with(listGroupModel.items[0]) {
            title.shouldBe("Push Notifications")
          }
          // Sms
          with(listGroupModel.items[1]) {
            title.shouldBe("Text Messages")
          }
          // Email
          with(listGroupModel.items[2]) {
            title.shouldBe("Emails")
          }
        }
      }
    }
  }

  test("sms open and close") {
    stateMachine.test(props) {
      // Setup instructions
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          // Sms
          with(listGroupModel.items[1]) {
            title.shouldBe("Text Messages")
            onClick.shouldNotBeNull().invoke()
          }
        }
      }

      // Entering and verifying phone number
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(PhoneNumber)
        entryPoint.shouldBeInstanceOf<Onboarding>()
        onClose()
      }

      // Back to setup instructions
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("sms open and skip") {
    stateMachine.test(props) {
      // Setup instructions
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          // Sms
          with(listGroupModel.items[1]) {
            title.shouldBe("Text Messages")
            onClick.shouldNotBeNull().invoke()
          }
        }
      }

      // Entering and verifying phone number
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(PhoneNumber)
        val skipBottomSheet =
          entryPoint.shouldBeInstanceOf<Onboarding>()
            .skipBottomSheetProvider.shouldNotBeNull().invoke()
        val formModel: FormBodyModel = skipBottomSheet.body as FormBodyModel
        with(formModel.header.shouldNotBeNull()) {
          headline.shouldBe("Are you sure you want to skip?")
          sublineModel.shouldNotBeNull().string.shouldBe(
            "Providing a phone number significantly enhances your wallet’s" +
              " security and your ability to recover your funds in case of any unforeseen issues."
          )
        }
        formModel.primaryButton.shouldNotBeNull().text.shouldBe("Go Back")
        formModel.secondaryButton.shouldNotBeNull().text.shouldBe("Skip")
        formModel.secondaryButton.shouldNotBeNull().onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PHONE_NUMBER_INPUT_SKIP)
      )

      // Back to setup instructions with sms now skipped
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          // Sms
          with(listGroupModel.items[1]) {
            leadingAccessory.shouldBe(
              ListItemAccessory.IconAccessory(
                model = IconModel(icon = SmallIconXFilled, iconSize = Small, iconTint = On60)
              )
            )
            treatment.shouldBe(SECONDARY)
          }
        }
      }
    }
  }

  test("email state machine can be shown and completes") {
    stateMachine.test(props) {
      // Setup instructions
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          // Email
          with(listGroupModel.items[2]) {
            title.shouldBe("Emails")
            onClick.shouldNotBeNull().invoke()
          }
        }
      }

      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(Email)
        entryPoint.shouldBeInstanceOf<Onboarding>()
        onClose()
      }

      // Back to setup instructions
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("email state machine is shown and skipped") {
    stateMachine.test(props) {
      // Setup instructions
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          // Email
          with(listGroupModel.items[2]) {
            title.shouldBe("Emails")
            onClick.shouldNotBeNull().invoke()
          }
        }
      }

      // entering and verifying email
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(Email)
        val skipBottomSheet =
          entryPoint.shouldBeInstanceOf<Onboarding>()
            .skipBottomSheetProvider.shouldNotBeNull().invoke()
        val formModel: FormBodyModel = skipBottomSheet.body as FormBodyModel
        with(formModel.header.shouldNotBeNull()) {
          headline.shouldBe("Are you sure you want to skip?")
          sublineModel.shouldNotBeNull().string.shouldBe(
            "Providing an email address significantly enhances your wallet’s" +
              " security and your ability to recover your funds in case of any unforeseen issues."
          )
        }
        formModel.primaryButton.shouldNotBeNull().text.shouldBe("Go Back")
        formModel.secondaryButton.shouldNotBeNull().text.shouldBe("Skip")
        formModel.secondaryButton.shouldNotBeNull().onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_EMAIL_INPUT_SKIP)
      )

      // notifications screen with email in skipped state
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          with(listGroupModel.items[2]) {
            leadingAccessory.shouldBe(
              ListItemAccessory.IconAccessory(
                model = IconModel(icon = SmallIconXFilled, iconSize = Small, iconTint = On60)
              )
            )
            treatment.shouldBe(SECONDARY)
          }
        }
      }
    }
  }

  test("push notification permission grant event tracked") {
    stateMachine.test(props) {
      // Setup instructions (first with initial push model before it updates)
      awaitScreenWithBody<FormBodyModel>()
      pushItemModelProvider.stateFlow.emit(NeedsAction)
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          // Push
          with(listGroupModel.items[0]) {
            title.shouldBe("Push Notifications")
            onClick.shouldNotBeNull().invoke()
          }
        }
      }

      notificationPermissionRequester.requestNotificationPermissionCalls.awaitItem()
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
      )
    }
  }

  test("push notification permission deny event tracked") {
    notificationPermissionRequester.successful = false
    stateMachine.test(props) {
      // Setup instructions (first with initial push model before it updates)
      awaitScreenWithBody<FormBodyModel>()
      pushItemModelProvider.stateFlow.emit(NeedsAction)
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          // Push
          with(listGroupModel.items[0]) {
            title.shouldBe("Push Notifications")
            onClick.shouldNotBeNull().invoke()
          }
        }
      }

      notificationPermissionRequester.requestNotificationPermissionCalls.awaitItem()
      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_DISABLED)
      )
    }
  }

  test("all items complete") {
    stateMachine.test(props) {
      // Setup instructions
      awaitScreenWithBody<FormBodyModel>()

      // Emit phone number to indicate it is complete
      notificationTouchpointDao.phoneNumberFlow.emit(PhoneNumberMock)

      // Setup instructions showing complete UI for phone
      awaitScreenWithBody<FormBodyModel>()

      // Emit push as complete
      pushItemModelProvider.stateFlow.emit(Completed)

      // Setup instructions showing complete UI for push
      awaitScreenWithBody<FormBodyModel>()

      notificationTouchpointDao.emailFlow.emit(EmailFake)

      // UI update for email completion
      awaitScreenWithBody<FormBodyModel>()

      onboardingKeyboxStepStateDao.setStateForStepCalls.awaitItem()
        .shouldBeInstanceOf<Pair<OnboardingKeyboxStep, OnboardingKeyboxStepState>>()
        .shouldBe(Pair(NotificationPreferences, Complete))
      onCompleteCalls.awaitItem()
    }
  }

  test("phone skipped, email and push complete") {
    stateMachine.test(props) {
      // Setup instructions
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first().shouldBeInstanceOf<ListGroup>().listGroupModel.items[1]
          .onClick.shouldNotBeNull().invoke()
      }

      // Entering and verifying phone number, perform skip
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(PhoneNumber)
        val formModel =
          entryPoint.shouldBeInstanceOf<Onboarding>()
            .skipBottomSheetProvider.shouldNotBeNull().invoke().body as FormBodyModel
        formModel.secondaryButton.shouldNotBeNull().onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PHONE_NUMBER_INPUT_SKIP)
      )

      // Setup instructions showing complete UI for phone
      awaitScreenWithBody<FormBodyModel>()

      // Emit push as complete
      pushItemModelProvider.stateFlow.emit(Completed)

      // Setup instructions showing complete UI for push
      awaitScreenWithBody<FormBodyModel>()

      notificationTouchpointDao.emailFlow.emit(EmailFake)

      // UI update for email completion
      awaitScreenWithBody<FormBodyModel>()

      onboardingKeyboxStepStateDao.setStateForStepCalls.awaitItem()
        .shouldBeInstanceOf<Pair<OnboardingKeyboxStep, OnboardingKeyboxStepState>>()
        .shouldBe(Pair(NotificationPreferences, Complete))

      onCompleteCalls.awaitItem()
    }
  }

  test("email skipped, phone and push complete") {
    stateMachine.test(props) {
      // Setup instructions
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first().shouldBeInstanceOf<ListGroup>().listGroupModel.items[2]
          .onClick.shouldNotBeNull().invoke()
      }

      // Entering and verifying email, perform skip
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(Email)
        val formModel =
          entryPoint.shouldBeInstanceOf<Onboarding>()
            .skipBottomSheetProvider.shouldNotBeNull().invoke().body as FormBodyModel
        formModel.secondaryButton.shouldNotBeNull().onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_EMAIL_INPUT_SKIP)
      )

      // Setup instructions showing complete UI for phone
      awaitScreenWithBody<FormBodyModel>()

      // Emit push as complete
      pushItemModelProvider.stateFlow.emit(Completed)

      // Setup instructions showing complete UI for push
      awaitScreenWithBody<FormBodyModel>()

      notificationTouchpointDao.phoneNumberFlow.emit(PhoneNumberMock)

      // UI update for phone number completion
      awaitScreenWithBody<FormBodyModel>()

      onboardingKeyboxStepStateDao.setStateForStepCalls.awaitItem()
        .shouldBeInstanceOf<Pair<OnboardingKeyboxStep, OnboardingKeyboxStepState>>()
        .shouldBe(Pair(NotificationPreferences, Complete))
      onCompleteCalls.awaitItem()
    }
  }

  test("phone skipped, try to skip email") {
    stateMachine.test(props) {
      // Setup instructions
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first().shouldBeInstanceOf<ListGroup>().listGroupModel.items[1]
          .onClick.shouldNotBeNull().invoke()
      }

      // Entering and verifying phone number, perform skip
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(PhoneNumber)
        val formModel =
          entryPoint.shouldBeInstanceOf<Onboarding>()
            .skipBottomSheetProvider.shouldNotBeNull().invoke().body as FormBodyModel
        formModel.secondaryButton.shouldNotBeNull().onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_PHONE_NUMBER_INPUT_SKIP)
      )

      // Setup instructions showing skip UI for phone
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first().shouldBeInstanceOf<ListGroup>().listGroupModel.items[2]
          .onClick.shouldNotBeNull().invoke()
      }

      // Entering and verifying email
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(Email)
        val skipBottomSheet =
          entryPoint.shouldBeInstanceOf<Onboarding>()
            .skipBottomSheetProvider.shouldNotBeNull().invoke().body as FormBodyModel
        skipBottomSheet.header.shouldNotBeNull().expectSkipNotAllowedModel()
        skipBottomSheet.primaryButton.shouldNotBeNull().text.shouldBe("Go Back")
        skipBottomSheet.secondaryButton.shouldNotBeNull().text.shouldBe("Enter phone number")
        skipBottomSheet.secondaryButton.shouldNotBeNull().onClick()
      }

      // Entering and verifying phone number
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(PhoneNumber)
      }
    }
  }

  test("email skipped, try to skip phone") {
    stateMachine.test(props) {
      // Setup instructions
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first().shouldBeInstanceOf<ListGroup>().listGroupModel.items[2]
          .onClick.shouldNotBeNull().invoke()
      }

      // Entering and verifying email, perform skip
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(Email)
        val skipBottomSheet =
          entryPoint.shouldBeInstanceOf<Onboarding>()
            .skipBottomSheetProvider.shouldNotBeNull().invoke().body as FormBodyModel
        skipBottomSheet.secondaryButton.shouldNotBeNull().onClick.invoke()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(
        TrackedAction(ACTION_APP_EMAIL_INPUT_SKIP)
      )

      // Setup instructions showing skip UI for email
      awaitScreenWithBody<FormBodyModel> {
        mainContentList.first().shouldBeInstanceOf<ListGroup>().listGroupModel.items[1]
          .onClick.shouldNotBeNull().invoke()
      }

      // Entering and verifying phone
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(PhoneNumber)
        val skipBottomSheet =
          entryPoint.shouldBeInstanceOf<Onboarding>()
            .skipBottomSheetProvider.shouldNotBeNull().invoke().body as FormBodyModel
        skipBottomSheet.header.shouldNotBeNull().expectSkipNotAllowedModel()
        skipBottomSheet.primaryButton.shouldNotBeNull().text.shouldBe("Go Back")
        skipBottomSheet.secondaryButton.shouldNotBeNull().text.shouldBe("Enter email")
        skipBottomSheet.secondaryButton.shouldNotBeNull().onClick()
      }

      // Entering and verifying email
      awaitScreenWithBodyModelMock<NotificationTouchpointInputAndVerificationProps> {
        touchpointType.shouldBe(Email)
      }
    }
  }
})

private fun FormHeaderModel.expectSkipNotAllowedModel() {
  headline.shouldBe("You need a phone number or email address to continue")
  sublineModel.shouldNotBeNull().string.shouldBe(
    "Providing a contact method significantly enhances your wallet’s security and" +
      " your ability to recover your funds in case of any unforeseen issues."
  )
}
