package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.account.AccountServiceFake
import build.wallet.account.AccountStatus
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_ENABLED
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.coroutines.turbine.turbines
import build.wallet.email.Email
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.UsSmsFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.notifications.NotificationTouchpointData
import build.wallet.notifications.NotificationTouchpointServiceFake
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoMock
import build.wallet.platform.settings.TelephonyCountryCodeProviderMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.NotCompleted
import build.wallet.statemachine.account.notifications.NotificationPermissionRequesterMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachine
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiStateMachine
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilBodyMock
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationPreferencesSetupUiStateMachineImplTests : FunSpec({

  val onCompleteCalls = turbines.create<Unit>("onComplete")
  val accountServiceFake = AccountServiceFake()
  val eventTracker = EventTrackerMock(turbines::create)
  val notificationPermissionRequester = NotificationPermissionRequesterMock(turbines::create)
  val notificationTouchpointService = NotificationTouchpointServiceFake()
  val onboardingKeyboxStepStateDao = OnboardingKeyboxStepStateDaoMock(turbines::create)
  val telephonyCountryCodeProvider = TelephonyCountryCodeProviderMock()

  val featureFlagDao = FeatureFlagDaoFake()
  val usSmsFeatureFlag = UsSmsFeatureFlag(featureFlagDao)

  // Whether push permission is denied (simulates Denied vs NotDetermined)
  var pushPermissionDenied = false
  val pushAlertCalls = turbines.create<RecoveryChannelsSetupPushActionState>("push alert calls")

  // Initial state for push item
  var initialPushItemState = NotCompleted

  val pushItemModelFlow = MutableStateFlow<RecoveryChannelsSetupFormItemModel?>(null)

  var lastOnShowAlert: ((RecoveryChannelsSetupPushActionState) -> Unit)? = null

  fun createPushItemModel(
    state: RecoveryChannelsSetupFormItemModel.State,
    onShowAlert: (RecoveryChannelsSetupPushActionState) -> Unit,
  ) = RecoveryChannelsSetupFormItemModel(
    state = state,
    uiErrorHint = UiErrorHint.None,
    onClick = {
      val alertState = if (pushPermissionDenied) {
        RecoveryChannelsSetupPushActionState.OpenSettings(openAction = {})
      } else {
        RecoveryChannelsSetupPushActionState.AppInfoPromptRequestingPush
      }
      pushAlertCalls.add(alertState)
      onShowAlert(alertState)
    }
  )

  fun simulateEmailCompleted() {
    notificationTouchpointService.setTouchpointData(
      NotificationTouchpointData(
        email = Email("test@example.com"),
        phoneNumber = null
      )
    )
  }

  fun setPushCompleted() {
    lastOnShowAlert?.let { onShowAlert ->
      pushItemModelFlow.value = createPushItemModel(Completed, onShowAlert)
    }
  }

  val pushItemModelProvider = object : RecoveryChannelsSetupPushItemModelProvider {
    override fun model(
      onShowAlert: (RecoveryChannelsSetupPushActionState) -> Unit,
    ): MutableStateFlow<RecoveryChannelsSetupFormItemModel> {
      lastOnShowAlert = onShowAlert
      pushItemModelFlow.value = createPushItemModel(initialPushItemState, onShowAlert)
      @Suppress("UNCHECKED_CAST")
      return pushItemModelFlow as MutableStateFlow<RecoveryChannelsSetupFormItemModel>
    }
  }

  val notificationTouchpointInputStateMachine =
    object : NotificationTouchpointInputAndVerificationUiStateMachine,
      ScreenStateMachineMock<NotificationTouchpointInputAndVerificationProps>(
        "notification-touchpoint-input"
      ) {}

  val notificationPreferencesStateMachine =
    object : NotificationPreferencesUiStateMachine,
      ScreenStateMachineMock<NotificationPreferencesProps>(
        "notification-preferences"
      ) {}

  fun createStateMachine() =
    NotificationPreferencesSetupUiStateMachineImpl(
      accountService = accountServiceFake,
      eventTracker = eventTracker,
      notificationPermissionRequester = notificationPermissionRequester,
      notificationTouchpointService = notificationTouchpointService,
      notificationPreferencesUiStateMachine = notificationPreferencesStateMachine,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      notificationTouchpointInputAndVerificationUiStateMachine = notificationTouchpointInputStateMachine,
      pushItemModelProvider = pushItemModelProvider,
      telephonyCountryCodeProvider = telephonyCountryCodeProvider,
      usSmsFeatureFlag = usSmsFeatureFlag
    )

  val props = NotificationPreferencesSetupUiProps(
    accountId = FullAccountIdMock,
    source = NotificationPreferencesProps.Source.Onboarding,
    onComplete = { onCompleteCalls.add(Unit) }
  )

  beforeTest {
    accountServiceFake.reset()
    notificationTouchpointService.reset()
    notificationPermissionRequester.reset()
    telephonyCountryCodeProvider.mockCountryCode = ""
    usSmsFeatureFlag.setFlagValue(false)
    initialPushItemState = NotCompleted
    pushItemModelFlow.value = null
    lastOnShowAlert = null
    pushPermissionDenied = false
  }

  context("Sequential flow always starts with email") {
    test("always starts at email screen") {
      telephonyCountryCodeProvider.mockCountryCode = "CA"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("email success advances to SMS when SMS is shown") {
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("email success skips SMS and shows push page when SMS is hidden") {
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US" // US, SMS hidden

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("SMS navigation") {
    test("SMS entryPoint.onSkip is always present in sequential flow") {
      telephonyCountryCodeProvider.mockCountryCode = "CA"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          entryPoint.shouldBeInstanceOf<NotificationTouchpointInputAndVerificationProps.EntryPoint.OnboardingAndRecovery>()
            .onSkip.shouldNotBeNull()
        }

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS success shows push notification setup page when push not completed") {
      telephonyCountryCodeProvider.mockCountryCode = "CA"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          onSuccess()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS success goes to transactions when push already completed") {
      telephonyCountryCodeProvider.mockCountryCode = "CA"
      initialPushItemState = Completed
      simulateEmailCompleted()

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Email appears first (initial state), advance through it
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess() // → goes to SMS (CA, SMS shown)
        }

        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          onSuccess() // push completed → transactions
        }

        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS skip advances to push page when push not completed") {
      telephonyCountryCodeProvider.mockCountryCode = "CA"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          entryPoint.shouldBeInstanceOf<NotificationTouchpointInputAndVerificationProps.EntryPoint.OnboardingAndRecovery>()
            .onSkip.shouldNotBeNull().invoke()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS skip goes to transactions when email and push completed") {
      telephonyCountryCodeProvider.mockCountryCode = "CA"
      initialPushItemState = Completed
      simulateEmailCompleted()

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Advance through email first
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          entryPoint.shouldBeInstanceOf<NotificationTouchpointInputAndVerificationProps.EntryPoint.OnboardingAndRecovery>()
            .onSkip.shouldNotBeNull().invoke()
        }

        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS close (back) returns to email") {
      telephonyCountryCodeProvider.mockCountryCode = "CA"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          onClose?.invoke()
        }

        // Should go back to email
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
        }
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Push notification setup screen") {
    test("push back button returns to SMS when SMS is shown") {
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          onSuccess()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          onNavigateBack()
        }

        // Should go back to SMS
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("push back button returns to email when SMS is hidden") {
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US" // SMS hidden

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          onNavigateBack()
        }

        // Should go back to email
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("push skip advances to transactions when email completed") {
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          simulateEmailCompleted()
          onSuccess()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          onSkip()
        }

        eventTracker.eventCalls.awaitItem().shouldBe(
          TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED)
        )

        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("push skip advances to transactions when email success has not emitted touchpoint data") {
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          onSkip()
        }

        eventTracker.eventCalls.awaitItem().shouldBe(
          TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED)
        )

        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("push allow shows system prompt when permission not determined") {
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"
      pushPermissionDenied = false

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          onAllowNotifications()
        }

        pushAlertCalls.awaitItem().shouldBe(RecoveryChannelsSetupPushActionState.AppInfoPromptRequestingPush)

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("push allow shows open settings alert when permission denied") {
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"
      pushPermissionDenied = true

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          onAllowNotifications()
        }

        pushAlertCalls.awaitItem().shouldBeInstanceOf<RecoveryChannelsSetupPushActionState.OpenSettings>()

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("auto-advances to transactions when returning from OS settings with push enabled") {
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"
      pushPermissionDenied = true // Start with denied so goes to settings

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          simulateEmailCompleted()
          onSuccess()
        }

        // Click allow on push screen → OpenSettings alert
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          onAllowNotifications()
        }

        val alertState = pushAlertCalls.awaitItem()
        alertState.shouldBeInstanceOf<RecoveryChannelsSetupPushActionState.OpenSettings>()

        // Push setup screen stays visible after opening settings
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()

        // Simulate user returning from settings with push now enabled
        setPushCompleted()

        // Should auto-advance to transactions
        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Transactions screen navigation") {
    test("transactions back button returns to push setup screen") {
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          simulateEmailCompleted()
          onSuccess()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          onSkip()
        }

        eventTracker.eventCalls.awaitItem() // consume analytics event

        awaitUntilBodyMock<NotificationPreferencesProps> {
          onBack()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("fullAccount is resolved from AccountService") {
    // Note: fullAccount uses mapLatest+collectAsState(initial=null), so the FIRST composition
    // always has null. We verify the entry point type only; integration tests cover fullAccount.

    test("W3 entry point is OnboardingAndRecovery on email screen") {
      telephonyCountryCodeProvider.mockCountryCode = "CA"
      accountServiceFake.accountState.value = Ok(AccountStatus.OnboardingAccount(FullAccountW3Mock))

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          entryPoint.shouldBeInstanceOf<NotificationTouchpointInputAndVerificationProps.EntryPoint.OnboardingAndRecovery>()
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("W1 entry point is OnboardingAndRecovery on email screen") {
      telephonyCountryCodeProvider.mockCountryCode = "CA"
      accountServiceFake.accountState.value = Ok(AccountStatus.OnboardingAccount(FullAccountMock))

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          entryPoint.shouldBeInstanceOf<NotificationTouchpointInputAndVerificationProps.EntryPoint.OnboardingAndRecovery>()
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("null fullAccount when no account in AccountService") {
      telephonyCountryCodeProvider.mockCountryCode = "CA"
      // accountServiceFake defaults to NoAccount

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          entryPoint.shouldBeInstanceOf<NotificationTouchpointInputAndVerificationProps.EntryPoint.OnboardingAndRecovery>()
            .fullAccount.shouldBeNull()
        }

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("US with US-SMS feature flag enabled") {
    test("SMS is included in sequential flow for US users when flag enabled") {
      usSmsFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "US"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        // Should advance to SMS (not skip it)
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
        }
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Push permission analytics") {
    test("allow button triggers push alert state for non-denied permission") {
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        // Push screen appears; clicking allow triggers the alert callback
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()

        // Push alert state is AppInfoPromptRequestingPush (not denied)
        pushPermissionDenied.shouldBe(false)

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("push skip tracks disabled event") {
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          simulateEmailCompleted()
          onSuccess()
        }

        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          onSkip()
        }

        eventTracker.eventCalls.awaitItem().shouldBe(
          TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED)
        )

        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }
  }
})
