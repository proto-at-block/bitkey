package build.wallet.statemachine.account.create.full.onboard.notifications

import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationPreferences
import bitkey.notifications.NotificationsPreferencesCachedProviderMock
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.auth.ActionProofHeader
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.feature.flags.DesignSystemUpdatesFeatureFlag
import build.wallet.platform.permissions.PermissionCheckerMock
import build.wallet.platform.settings.SystemSettingsLauncherMock
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.account.notifications.NotificationPermissionRequesterMock
import build.wallet.statemachine.auth.HardwareAuthUiProps
import build.wallet.statemachine.auth.HardwareAuthUiStateMachine
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachineImpl
import build.wallet.statemachine.notifications.shouldShowNotificationPreferencesOnboardingTos
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.list.ListItemAccessory
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class NotificationPreferencesUiStateMachineImplTests : FunSpec({
  val hardwareAuthUiStateMachine =
    object : HardwareAuthUiStateMachine,
      ScreenStateMachineMock<HardwareAuthUiProps>("hardware-auth") {}

  val notificationsPreferencesCachedProvider = NotificationsPreferencesCachedProviderMock()
  val notificationPermissionRequester = NotificationPermissionRequesterMock(turbines::create)
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val eventTracker = EventTrackerMock(turbines::create)
  val featureFlagDao = FeatureFlagDaoFake()
  val designSystemUpdatesFeatureFlag = DesignSystemUpdatesFeatureFlag(featureFlagDao)

  val stateMachine = NotificationPreferencesUiStateMachineImpl(
    permissionChecker = PermissionCheckerMock(),
    notificationsPreferencesCachedProvider = notificationsPreferencesCachedProvider,
    systemSettingsLauncher = SystemSettingsLauncherMock(),
    notificationPermissionRequester = notificationPermissionRequester,
    inAppBrowserNavigator = inAppBrowserNavigator,
    eventTracker = eventTracker,
    hardwareAuthUiStateMachine = hardwareAuthUiStateMachine,
    designSystemUpdatesFeatureFlag = designSystemUpdatesFeatureFlag
  )

  val onCompleteCalls = turbines.create<Unit>("onComplete")

  beforeTest {
    designSystemUpdatesFeatureFlag.setFlagValue(BooleanFlag(false))
  }

  val props = NotificationPreferencesProps(
    accountId = FullAccountIdMock,
    source = NotificationPreferencesProps.Source.Onboarding,
    onBack = {},
    onComplete = { onCompleteCalls.add(Unit) }
  )

  test("show tos if terms not accepted") {
    stateMachine.testWithVirtualTime(props) {
      // Try and hit "Continue" right away
      awaitBody<FormBodyModel> {
        ctaWarning.shouldBeNull()
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Assert that we show some terms
      awaitBody<FormBodyModel> {
        ctaWarning.shouldNotBeNull().text.shouldBe("Agree to our Terms and Privacy Policy to continue.")

        // Simulate tapping the ToS button
        val tosListGroup = mainContentList[4].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        tosListGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.IconAccessory>().onClick.shouldNotBeNull().invoke()
      }

      // Terms warning should go away
      awaitBody<FormBodyModel> {
        ctaWarning.shouldBeNull()
      }

      // Icon should be filled
      awaitBody<FormBodyModel> {
        val tosListGroup = mainContentList[4].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        tosListGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.IconAccessory>()
          .model.iconImage.shouldBe(IconImage.LocalImage(Icon.SmallIconCheckFilled))
      }
    }
  }

  test("calls onComplete when done - onboarding skips action proof") {
    stateMachine.testWithVirtualTime(props) {
      awaitBody<FormBodyModel> {
        // Simulate tapping the ToS button
        val tosListGroup = mainContentList[4].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        tosListGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.IconAccessory>().onClick.shouldNotBeNull().invoke()

        ctaWarning.shouldBeNull()
      }

      // Re-render the screen with the TOS selected
      awaitBody<FormBodyModel> {
        // Tap Continue
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Transition to a loading state, where the primary button shows a loading spinner
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      // Once more go back to the editing state
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeFalse()
      }
      // Finally, onComplete is called.
      onCompleteCalls.awaitItem()
    }
  }

  test("dsv2 onboarding skips the notification tos row") {
    shouldShowNotificationPreferencesOnboardingTos(
      source = NotificationPreferencesProps.Source.Onboarding,
      isDesignSystemV2Enabled = true
    ).shouldBeFalse()

    shouldShowNotificationPreferencesOnboardingTos(
      source = NotificationPreferencesProps.Source.Onboarding,
      isDesignSystemV2Enabled = false
    ).shouldBeTrue()
  }

  test("dsv2 enabled hides tos and allows continue without tos acceptance") {
    designSystemUpdatesFeatureFlag.setFlagValue(BooleanFlag(true))

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

      // Back to editing after success
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeFalse()
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
      hardwareAuthUiStateMachine = hardwareAuthUiStateMachine,
      designSystemUpdatesFeatureFlag = designSystemUpdatesFeatureFlag
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
        val transactionPushGroup = mainContentList[1].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        transactionPushGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.SwitchAccessory>().model.onCheckedChange.invoke(false)
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

      // Back to editing after success
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeFalse()
      }

      // No analytics event for disabling push (events are only fired when enabling channels)

      // onComplete is called
      onCompleteCalls.awaitItem()

      // Verify proof was passed through
      provider.lastUpdateProof.shouldNotBeNull()
        .shouldBeInstanceOf<PrivilegedActionProof.HwSignedAction>()
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
      hardwareAuthUiStateMachine = hardwareAuthUiStateMachine,
      designSystemUpdatesFeatureFlag = designSystemUpdatesFeatureFlag
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
        val transactionPushGroup = mainContentList[1].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        transactionPushGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.SwitchAccessory>().model.onCheckedChange.invoke(false)
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
      awaitBody<FormBodyModel> {
        // Simulate tapping the ToS button
        val tosListGroup = mainContentList[4].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        tosListGroup.listGroupModel.items.first().trailingAccessory.shouldNotBeNull()
          .shouldBeInstanceOf<ListItemAccessory.IconAccessory>().onClick.shouldNotBeNull().invoke()
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick.shouldNotBeNull().invoke()
      }

      // Should go straight to loading (no hardware auth), because source is Onboarding
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeTrue()
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isLoading.shouldBeFalse()
      }
      onCompleteCalls.awaitItem()
    }
  }
})
