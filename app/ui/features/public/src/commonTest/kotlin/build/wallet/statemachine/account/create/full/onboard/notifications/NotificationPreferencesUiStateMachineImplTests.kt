package build.wallet.statemachine.account.create.full.onboard.notifications

import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationPreferences
import bitkey.notifications.NotificationsPreferencesCachedProvider
import bitkey.notifications.NotificationsPreferencesCachedProviderMock
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.ActionProofHeader
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.NetworkingError
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.platform.settings.SystemSettingsLauncherMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.notifications.NotificationPermissionRequesterMock
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachineImpl
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.switch.SwitchModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationPreferencesUiStateMachineImplTests : FunSpec({
  val hardwareAuthUiStateMachine =
    object : HardwareAuthUiStateMachine,
      ScreenStateMachineMock<HardwareAuthUiProps>("hardware-auth") {}

  val notificationsPreferencesCachedProvider = NotificationsPreferencesCachedProviderMock()
  val notificationPermissionRequester = NotificationPermissionRequesterMock(turbines::create)
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)

  val stateMachine = NotificationPreferencesUiStateMachineImpl(
    permissionChecker = PermissionCheckerMock(),
    notificationsPreferencesCachedProvider = notificationsPreferencesCachedProvider,
    systemSettingsLauncher = SystemSettingsLauncherMock(),
    notificationPermissionRequester = notificationPermissionRequester,
    inAppBrowserNavigator = inAppBrowserNavigator,
    eventTracker = eventTracker,
    hardwareAuthUiStateMachine = hardwareAuthUiStateMachine
  )

  val onCompleteCalls = turbines.create<Unit>("onComplete")

  val props = NotificationPreferencesProps(
    accountId = FullAccountIdMock,
    source = NotificationPreferencesProps.Source.Onboarding,
    onBack = {},
    onComplete = { onCompleteCalls.add(Unit) }
  )

  test("calls onComplete when done - onboarding skips action proof") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        // Tap Continue
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Transition to a loading state, where the primary button shows a loading spinner
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()

        val transactionPushGroup =
          mainContentList[1].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        val transactionPushToggle =
          transactionPushGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
            .shouldBeInstanceOf<ListItemAccessory.SwitchAccessory>()
            .model
            .shouldBeInstanceOf<SwitchModel>()
        transactionPushToggle.enabled.shouldBeTrue()
        transactionPushToggle.interactionsEnabled.shouldBeFalse()
      }

      // Finally, onComplete is called.
      onCompleteCalls.awaitItem()
    }
  }

  test("flow progresses to loading when primary button is clicked") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        // TOS list group should not be present (only 4 content items instead of 5)
        mainContentList.size.shouldBe(4)

        // Should be able to continue without accepting TOS
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Transition to loading
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      onCompleteCalls.awaitItem()
    }
  }

  test("settings source triggers action proof signing flow") {
    val currentPrefs = NotificationPreferences(
      moneyMovement = setOf(NotificationChannel.Push),
      productMarketing = emptySet()
    )
    val provider = NotificationsPreferencesCachedProviderMock(
      getNotificationPreferencesResult = Ok(currentPrefs)
    )

    val sm = NotificationPreferencesUiStateMachineImpl(
      permissionChecker = PermissionCheckerMock(),
      notificationsPreferencesCachedProvider = provider,
      systemSettingsLauncher = SystemSettingsLauncherMock(),
      notificationPermissionRequester = notificationPermissionRequester,
      inAppBrowserNavigator = inAppBrowserNavigator,
      eventTracker = eventTracker,
      hardwareAuthUiStateMachine = hardwareAuthUiStateMachine
    )

    val settingsProps = NotificationPreferencesProps(
      accountId = FullAccountIdMock,
      source = NotificationPreferencesProps.Source.Settings,
      onBack = {},
      onComplete = { onCompleteCalls.add(Unit) },
      fullAccount = FullAccountMock
    )

    sm.testWithVirtualTime(settingsProps) {
      // Wait for loading to finish and preferences to load
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      // Now in editing state with loaded preferences — disable the transaction push toggle
      // so that action proof is required (push is a security-reducing operation).
      awaitBody<FormBodyModel> {
        val transactionPushGroup =
          mainContentList[1].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        transactionPushGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.SwitchAccessory>().model.onCheckedChange.invoke(
            false
          )
      }

      // Re-render after toggling push off — tap Continue to trigger action proof flow
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Should transition to hardware auth (action proof signing)
      awaitBodyMock<HardwareAuthUiProps>(id = "hardware-auth") {
        actionDescription.shouldBe("Updating notification preferences")
        fullAccountId.shouldBe(FullAccountMock.accountId)

        // Simulate successful signing
        onSuccess(
          PrivilegedActionProof.HwSignedAction(
            actionProof = ActionProofHeader(
              signatures = listOf("0".repeat(130)),
              nonce = null
            )
          )
        )
      }

      // Should transition to loading while sending to server
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      // No analytics event for disabling push (events are only fired when enabling channels)

      // onComplete is called
      onCompleteCalls.awaitItem()

      // Verify proof was passed through
      provider.lastUpdateProof.shouldNotBeNull()
        .shouldBeInstanceOf<PrivilegedActionProof.HwSignedAction>()
    }
  }

  test("settings action proof submit keeps latest refreshed account security state") {
    val currentPrefs = NotificationPreferences(
      moneyMovement = setOf(NotificationChannel.Push),
      productMarketing = emptySet(),
      accountSecurity = setOf(NotificationChannel.Push)
    )
    val refreshedPrefs = currentPrefs.copy(accountSecurity = setOf(NotificationChannel.Email))
    val provider = NotificationsPreferencesCachedProviderMock(
      getNotificationPreferencesResult = Ok(currentPrefs)
    )

    val sm = NotificationPreferencesUiStateMachineImpl(
      permissionChecker = PermissionCheckerMock(),
      notificationsPreferencesCachedProvider = provider,
      systemSettingsLauncher = SystemSettingsLauncherMock(),
      notificationPermissionRequester = notificationPermissionRequester,
      inAppBrowserNavigator = inAppBrowserNavigator,
      eventTracker = eventTracker,
      hardwareAuthUiStateMachine = hardwareAuthUiStateMachine
    )

    val settingsProps = NotificationPreferencesProps(
      accountId = FullAccountIdMock,
      source = NotificationPreferencesProps.Source.Settings,
      onBack = {},
      onComplete = { onCompleteCalls.add(Unit) },
      fullAccount = FullAccountMock
    )

    sm.testWithVirtualTime(settingsProps) {
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      awaitBody<FormBodyModel> {
        val transactionPushGroup =
          mainContentList[1].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        transactionPushGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.SwitchAccessory>().model.onCheckedChange.invoke(
            false
          )
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      awaitBodyMock<HardwareAuthUiProps>(id = "hardware-auth") {
        provider.notificationPreferences.value = Ok(refreshedPrefs)
      }

      awaitBodyMock<HardwareAuthUiProps>(id = "hardware-auth") {
        onSuccess(
          PrivilegedActionProof.HwSignedAction(
            actionProof = ActionProofHeader(
              signatures = listOf("0".repeat(130)),
              nonce = null
            )
          )
        )
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      onCompleteCalls.awaitItem()

      provider.lastUpdatePreferences.shouldNotBeNull().apply {
        moneyMovement.shouldBe(emptySet())
        accountSecurity.shouldBe(refreshedPrefs.accountSecurity)
      }
    }
  }

  test("settings retry keeps latest account security refresh after failed submit") {
    val cachedPrefs = NotificationPreferences(
      moneyMovement = setOf(NotificationChannel.Push),
      productMarketing = emptySet(),
      accountSecurity = setOf(NotificationChannel.Push)
    )
    val refreshedPrefs = cachedPrefs.copy(accountSecurity = setOf(NotificationChannel.Email))
    val updateResults =
      Channel<com.github.michaelbull.result.Result<Unit, NetworkingError>>(capacity = 2)
    val notificationPreferences =
      MutableStateFlow<com.github.michaelbull.result.Result<NotificationPreferences, Error>?>(
        Ok(cachedPrefs)
      )
    var lastUpdatePreferences: NotificationPreferences? = null

    val provider = object : NotificationsPreferencesCachedProvider {
      override suspend fun initialize() = Unit

      override fun getNotificationsPreferences() = notificationPreferences

      override suspend fun updateNotificationsPreferences(
        accountId: build.wallet.bitkey.f8e.AccountId,
        preferences: NotificationPreferences,
        proof: PrivilegedActionProof?,
      ): com.github.michaelbull.result.Result<Unit, NetworkingError> {
        lastUpdatePreferences = preferences
        return updateResults.receive()
      }
    }

    val sm = NotificationPreferencesUiStateMachineImpl(
      permissionChecker = PermissionCheckerMock(),
      notificationsPreferencesCachedProvider = provider,
      systemSettingsLauncher = SystemSettingsLauncherMock(),
      notificationPermissionRequester = notificationPermissionRequester,
      inAppBrowserNavigator = inAppBrowserNavigator,
      eventTracker = eventTracker,
      hardwareAuthUiStateMachine = hardwareAuthUiStateMachine
    )

    val settingsProps = NotificationPreferencesProps(
      accountId = FullAccountIdMock,
      source = NotificationPreferencesProps.Source.Settings,
      onBack = {},
      onComplete = { onCompleteCalls.add(Unit) }
    )

    sm.testWithVirtualTime(settingsProps) {
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      awaitBody<FormBodyModel> {
        val updatesGroup = mainContentList[3].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        updatesGroup.listGroupModel.items[1].trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.SwitchAccessory>()
          .model
          .onCheckedChange
          .invoke(true)
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
        notificationPreferences.value = Ok(refreshedPrefs)
        updateResults.trySend(Err(HttpError.NetworkError(Exception("network-error"))))
      }

      awaitItem().bottomSheetModel.shouldNotBeNull().onClosed()

      awaitBody<FormBodyModel> {
        val updatesGroup = mainContentList[3].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        updatesGroup.listGroupModel.items[1].trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.SwitchAccessory>()
          .model
          .checked
          .shouldBeTrue()

        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
        updateResults.trySend(Err(HttpError.NetworkError(Exception("network-error"))))
      }

      awaitItem().bottomSheetModel.shouldNotBeNull()

      lastUpdatePreferences.shouldNotBeNull().accountSecurity.shouldBe(
        refreshedPrefs.accountSecurity
      )
    }
  }

  test("settings source - cancel action proof returns to editing") {
    val currentPrefs = NotificationPreferences(
      moneyMovement = setOf(NotificationChannel.Push),
      productMarketing = emptySet()
    )
    val provider = NotificationsPreferencesCachedProviderMock(
      getNotificationPreferencesResult = Ok(currentPrefs)
    )

    val sm = NotificationPreferencesUiStateMachineImpl(
      permissionChecker = PermissionCheckerMock(),
      notificationsPreferencesCachedProvider = provider,
      systemSettingsLauncher = SystemSettingsLauncherMock(),
      notificationPermissionRequester = notificationPermissionRequester,
      inAppBrowserNavigator = inAppBrowserNavigator,
      eventTracker = eventTracker,
      hardwareAuthUiStateMachine = hardwareAuthUiStateMachine
    )

    val settingsProps = NotificationPreferencesProps(
      accountId = FullAccountIdMock,
      source = NotificationPreferencesProps.Source.Settings,
      onBack = {},
      onComplete = { onCompleteCalls.add(Unit) },
      fullAccount = FullAccountMock
    )

    sm.testWithVirtualTime(settingsProps) {
      // Loading
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      // Editing — disable transaction push so action proof is required on Continue
      awaitBody<FormBodyModel> {
        val transactionPushGroup =
          mainContentList[1].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        transactionPushGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.SwitchAccessory>().model.onCheckedChange.invoke(
            false
          )
      }

      // Re-render after toggle — tap Continue
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Hardware auth screen - simulate cancel
      awaitBodyMock<HardwareAuthUiProps>(id = "hardware-auth") {
        onBack()
      }

      // Should return to editing
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeFalse()
      }
    }
  }

  test("onboarding source does not trigger action proof even with fullAccount") {
    val onboardingWithAccountProps = NotificationPreferencesProps(
      accountId = FullAccountIdMock,
      source = NotificationPreferencesProps.Source.Onboarding,
      onBack = {},
      onComplete = { onCompleteCalls.add(Unit) },
      fullAccount = FullAccountMock
    )

    stateMachine.testWithVirtualTime(onboardingWithAccountProps) {
      // tap continue button
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Should go straight to loading (no hardware auth), because source is Onboarding
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      onCompleteCalls.awaitItem()
    }
  }
})
