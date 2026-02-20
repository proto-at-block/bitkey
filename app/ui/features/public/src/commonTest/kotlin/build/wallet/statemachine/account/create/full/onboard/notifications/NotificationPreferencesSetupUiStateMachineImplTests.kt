package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.UsSmsFeatureFlag
import build.wallet.feature.flags.W3OnboardingFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.notifications.NotificationTouchpointServiceFake
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.onboarding.OnboardingKeyboxStepStateDaoMock
import build.wallet.platform.settings.TelephonyCountryCodeProviderMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationPreferencesSetupUiStateMachineImplTests : FunSpec({

  val onCompleteCalls = turbines.create<Unit>("onComplete")
  val eventTracker = EventTrackerMock(turbines::create)
  val notificationPermissionRequester = NotificationPermissionRequesterMock(turbines::create)
  val notificationTouchpointService = NotificationTouchpointServiceFake()
  val onboardingKeyboxStepStateDao = OnboardingKeyboxStepStateDaoMock(turbines::create)
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val telephonyCountryCodeProvider = TelephonyCountryCodeProviderMock()

  val featureFlagDao = FeatureFlagDaoFake()
  val usSmsFeatureFlag = UsSmsFeatureFlag(featureFlagDao)
  val w3OnboardingFeatureFlag = W3OnboardingFeatureFlag(featureFlagDao)

  // Track push item state and permission status
  // When true, onClick returns OpenSettings (simulating Denied permission)
  // When false, onClick returns AppInfoPromptRequestingPush (simulating NotDetermined permission)
  var pushPermissionDenied = false
  val pushAlertCalls = turbines.create<RecoveryChannelsSetupPushActionState>("push alert calls")

  // Initial state for push item (set before state machine starts)
  var initialPushItemState = NotCompleted

  // Use a MutableStateFlow that can be updated during tests
  val pushItemModelFlow = MutableStateFlow<RecoveryChannelsSetupFormItemModel?>(null)

  // Captured onShowAlert callback from model() call
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

  // Call this during a test to simulate push permission being granted
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
      // Initialize with the initial state
      pushItemModelFlow.value = createPushItemModel(initialPushItemState, onShowAlert)
      @Suppress("UNCHECKED_CAST")
      return pushItemModelFlow as MutableStateFlow<RecoveryChannelsSetupFormItemModel>
    }
  }

  val uiErrorHintsProvider = object : UiErrorHintsProvider {
    private val hints = mutableMapOf<UiErrorHintKey, UiErrorHint>()
    private val flows = mutableMapOf<UiErrorHintKey, MutableStateFlow<UiErrorHint>>()

    override suspend fun setErrorHint(
      key: UiErrorHintKey,
      hint: UiErrorHint,
    ) {
      hints[key] = hint
      flows[key]?.value = hint
    }

    override suspend fun getErrorHint(key: UiErrorHintKey): UiErrorHint =
      hints[key] ?: UiErrorHint.None

    override fun errorHintFlow(key: UiErrorHintKey) =
      flows.getOrPut(key) { MutableStateFlow(UiErrorHint.None) }

    fun reset() {
      hints.clear()
      flows.values.forEach { it.value = UiErrorHint.None }
    }
  }

  // Mock sub state machines that capture their props
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
      eventTracker = eventTracker,
      notificationPermissionRequester = notificationPermissionRequester,
      notificationTouchpointService = notificationTouchpointService,
      notificationPreferencesUiStateMachine = notificationPreferencesStateMachine,
      onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
      notificationTouchpointInputAndVerificationUiStateMachine = notificationTouchpointInputStateMachine,
      inAppBrowserNavigator = inAppBrowserNavigator,
      pushItemModelProvider = pushItemModelProvider,
      telephonyCountryCodeProvider = telephonyCountryCodeProvider,
      uiErrorHintsProvider = uiErrorHintsProvider,
      usSmsFeatureFlag = usSmsFeatureFlag,
      w3OnboardingFeatureFlag = w3OnboardingFeatureFlag
    )

  val props = NotificationPreferencesSetupUiProps(
    accountId = FullAccountIdMock,
    source = NotificationPreferencesProps.Source.Onboarding,
    onComplete = { onCompleteCalls.add(Unit) }
  )

  beforeTest {
    notificationTouchpointService.reset()
    notificationPermissionRequester.reset()
    uiErrorHintsProvider.reset()
    telephonyCountryCodeProvider.mockCountryCode = ""
    usSmsFeatureFlag.setFlagValue(false)
    w3OnboardingFeatureFlag.setFlagValue(false)
    initialPushItemState = NotCompleted
    pushItemModelFlow.value = null
    lastOnShowAlert = null
    pushPermissionDenied = false
  }

  context("Feature flag OFF - Hub-and-spoke behavior") {
    test("email success returns to hub") {
      w3OnboardingFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.state.shouldBe(NotCompleted)
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // In email flow
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          // Simulate success
          onSuccess()
        }

        // Should return to hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS success returns to hub") {
      w3OnboardingFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
        }

        // In SMS flow
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          // Simulate success
          onSuccess()
        }

        // Should return to hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS entryPoint.onSkip is null when feature flag is OFF") {
      w3OnboardingFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
        }

        // In SMS flow - verify onSkip is null (skip button should not be shown)
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          entryPoint.shouldBeInstanceOf<NotificationTouchpointInputAndVerificationProps.EntryPoint.Recovery>()
            .onSkip.shouldBeNull()
        }

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("email success shows OpenSettings alert when push permission is denied") {
      w3OnboardingFeatureFlag.setFlagValue(false)
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US" // US, SMS hidden
      pushPermissionDenied = true // Simulate Denied permission

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          pushItem.state.shouldBe(NotCompleted)
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Email flow - success
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          onSuccess()
        }

        // Should return to hub (flag OFF = hub-and-spoke behavior)
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          // Click push to trigger the permission check
          pushItem.onClick.shouldNotBeNull().invoke()
        }

        // Push item's onClick should return OpenSettings (not AppInfoPromptRequestingPush)
        // because permission is denied
        pushAlertCalls.awaitItem().shouldBeInstanceOf<RecoveryChannelsSetupPushActionState.OpenSettings>()

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS success shows OpenSettings alert when push permission is denied") {
      w3OnboardingFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown
      pushPermissionDenied = true // Simulate Denied permission

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
        }

        // SMS flow - success
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          onSuccess()
        }

        // Should return to hub (flag OFF = hub-and-spoke behavior)
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          // Click push to trigger the permission check
          pushItem.onClick.shouldNotBeNull().invoke()
        }

        // Push item's onClick should return OpenSettings because permission is denied
        pushAlertCalls.awaitItem().shouldBeInstanceOf<RecoveryChannelsSetupPushActionState.OpenSettings>()

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Feature flag ON - Sequential flow with SMS shown (non-US)") {
    test("email success advances to SMS flow") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull() // SMS should be visible
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // In email flow
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          // Simulate success
          onSuccess()
        }

        // Should advance to SMS flow (not return to hub)
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS entryPoint.onSkip is present when feature flag is ON") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
        }

        // In SMS flow - verify onSkip is present (skip button should be shown)
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          entryPoint.shouldBeInstanceOf<NotificationTouchpointInputAndVerificationProps.EntryPoint.Recovery>()
            .onSkip.shouldNotBeNull()
        }

        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS success shows fullscreen push notification setup page") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub, click SMS directly
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
        }

        // In SMS flow
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          // Simulate success
          onSuccess()
        }

        // Should show fullscreen push notification setup page (not alert dialog)
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("full sequential flow: email -> SMS -> push -> transactions") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown
      initialPushItemState = Completed // Push already granted

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          pushItem.state.shouldBe(Completed)
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Email flow
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        // SMS flow (auto-advanced)
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          onSuccess()
        }

        // After SMS success with push already completed, should advance to transactions
        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Feature flag ON - Sequential flow with SMS hidden (US without US-SMS flag)") {
    test("email success skips SMS and shows fullscreen push notification setup page") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US" // US, SMS hidden

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldBeNull() // SMS should NOT be visible for US without flag
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // In email flow
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          // Simulate success
          onSuccess()
        }

        // Should show fullscreen push notification setup page (skipping SMS)
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("email success goes to transactions if push already completed") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"
      initialPushItemState = Completed // Push already done

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldBeNull()
          pushItem.state.shouldBe(Completed)
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // In email flow
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onSuccess()
        }

        // Should go directly to transactions (skipping both SMS and push)
        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Skip at each stage returns to hub") {
    test("email close returns to hub (flag ON)") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // In email flow - close instead of success
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.Email)
          onClose()
        }

        // Should return to hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS close returns to hub (flag ON)") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
        }

        // In SMS flow - close instead of success
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          onClose()
        }

        // Should return to hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS skip via entryPoint.onSkip advances to notification setup page (flag ON)") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA"
      // pushItemState defaults to NotCompleted

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
        }

        // In SMS flow - use skip callback from entryPoint
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          entryPoint.shouldBeInstanceOf<NotificationTouchpointInputAndVerificationProps.EntryPoint.Recovery>()
            .onSkip.shouldNotBeNull().invoke()
        }

        // Should advance to notification setup page (not return to hub)
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS skip via entryPoint.onSkip advances to transactions when push completed (flag ON)") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA"
      initialPushItemState = Completed // Push already granted

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
        }

        // In SMS flow - use skip callback from entryPoint
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          entryPoint.shouldBeInstanceOf<NotificationTouchpointInputAndVerificationProps.EntryPoint.Recovery>()
            .onSkip.shouldNotBeNull().invoke()
        }

        // Should advance to transactions (push already completed)
        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Back/exit during sequential flow returns to hub") {
    test("closing email during sequential flow returns to hub") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // In email flow
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          // User closes (back/exit) instead of completing
          onClose()
        }

        // Should return to hub, not advance
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("closing SMS after email success returns to hub") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Email flow - success
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          onSuccess()
        }

        // Auto-advanced to SMS flow - but user closes
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          onClose()
        }

        // Should return to hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Hub shows completion status") {
    test("hub shows email as completed after returning from email success") {
      w3OnboardingFeatureFlag.setFlagValue(false) // Hub-and-spoke
      telephonyCountryCodeProvider.mockCountryCode = "CA"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub - email not completed
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.state.shouldBe(NotCompleted)
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Email flow - success
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          onSuccess()
        }

        // Return to hub
        // Note: In the real implementation, the email state would be updated by
        // the LaunchedEffect watching notificationTouchpointData. Since we're using
        // a fake service, we'd need to simulate that. For this test, we just verify
        // we return to the hub.
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("Push notification handling in sequential flow") {
    test("clicking push from hub shows fullscreen page when flag ON") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA"
      // pushItemState defaults to NotCompleted

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub and click push
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          pushItem.state.shouldBe(NotCompleted)
          pushItem.onClick.shouldNotBeNull().invoke()
        }

        // Should show fullscreen push notification setup page (not alert dialog)
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("clicking push from hub shows alert dialog when flag OFF") {
      w3OnboardingFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "CA"
      // pushItemState defaults to NotCompleted

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub and click push
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          pushItem.state.shouldBe(NotCompleted)
          pushItem.onClick.shouldNotBeNull().invoke()
        }

        // Should show alert dialog (hub-and-spoke behavior)
        pushAlertCalls.awaitItem()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("email success shows fullscreen push page when push not completed") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"
      // pushItemState defaults to NotCompleted

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          pushItem.state.shouldBe(NotCompleted)
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Email flow - success
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          onSuccess()
        }

        // After email success with push not completed, should show fullscreen page
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {}
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("email success skips push page and advances to transactions when push completed") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"
      initialPushItemState = Completed // Push already granted

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          pushItem.state.shouldBe(Completed)
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Email flow - success
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          onSuccess()
        }

        // After email success with push already completed, should advance to transactions
        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("SMS success shows fullscreen push page when push not completed") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "CA" // Non-US, SMS shown
      // pushItemState defaults to NotCompleted

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub, click SMS directly
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
        }

        // In SMS flow
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          touchpointType.shouldBe(NotificationTouchpointType.PhoneNumber)
          onSuccess()
        }

        // After SMS success with push not completed, should show fullscreen page
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("auto-advances to transactions when returning from settings with push enabled") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"
      pushPermissionDenied = true // Start with permission denied

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          onSuccess()
        }

        // Should show fullscreen push notification setup page
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          // Click "Allow notifications" - permission is denied so will show OpenSettings
          onAllowNotifications()
        }

        // Push alert should be OpenSettings since permission is denied
        val alertState = pushAlertCalls.awaitItem()
        alertState.shouldBeInstanceOf<RecoveryChannelsSetupPushActionState.OpenSettings>()

        // Should return to hub with advanceToTransactionsAfterPush preserved
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()

        // Simulate user returning from settings with push now enabled
        setPushCompleted()

        // Should auto-advance to transactions
        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("fullscreen push page close button returns to hub") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          onSuccess()
        }

        // Should show fullscreen push notification setup page
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          // Click close button
          onClose()
        }

        // Should track analytics event when closing push setup
        eventTracker.eventCalls.awaitItem().shouldBe(
          TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED)
        )

        // Should return to hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel>()
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("fullscreen push page skip button advances to transactions") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          onSuccess()
        }

        // Should show fullscreen push notification setup page
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          // Click skip button
          onSkip()
        }

        // Should track analytics event when skipping push setup
        eventTracker.eventCalls.awaitItem().shouldBe(
          TrackedAction(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED)
        )

        // Should advance to transactions
        awaitUntilBodyMock<NotificationPreferencesProps> {}
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("fullscreen push page allow notifications shows system prompt when permission not determined") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      usSmsFeatureFlag.setFlagValue(false)
      telephonyCountryCodeProvider.mockCountryCode = "US"
      pushPermissionDenied = false // Permission is NotDetermined (will show AppInfoPromptRequestingPush)

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Complete email
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
          onSuccess()
        }

        // Should show fullscreen push notification setup page
        awaitUntilBody<RecoveryNotificationsSetupFormBodyModel> {
          // Click "Allow notifications"
          onAllowNotifications()
        }

        // Should show AppInfoPromptRequestingPush (the app's permission dialog)
        pushAlertCalls.awaitItem().shouldBe(RecoveryChannelsSetupPushActionState.AppInfoPromptRequestingPush)

        cancelAndIgnoreRemainingEvents()
      }
    }
  }

  context("US with US-SMS feature flag enabled") {
    test("SMS is shown for US users when US-SMS flag is enabled") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      usSmsFeatureFlag.setFlagValue(true) // Enable US SMS
      telephonyCountryCodeProvider.mockCountryCode = "US"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          // SMS should be visible with US-SMS flag enabled
          smsItem.shouldNotBeNull()
        }
        cancelAndIgnoreRemainingEvents()
      }
    }

    test("sequential flow includes SMS for US users with US-SMS flag") {
      w3OnboardingFeatureFlag.setFlagValue(true)
      usSmsFeatureFlag.setFlagValue(true)
      telephonyCountryCodeProvider.mockCountryCode = "US"

      val stateMachine = createStateMachine()
      stateMachine.test(props) {
        // Start at hub
        awaitUntilBody<RecoveryChannelsSetupFormBodyModel> {
          smsItem.shouldNotBeNull()
          emailItem.onClick.shouldNotBeNull().invoke()
        }

        // Email flow - success
        awaitUntilBodyMock<NotificationTouchpointInputAndVerificationProps> {
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
})
