package build.wallet.statemachine.account.create.full.onboard.notifications

import androidx.compose.runtime.*
import bitkey.notifications.NotificationChannel
import build.wallet.account.AccountService
import build.wallet.account.AccountStatus
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.*
import build.wallet.bitkey.account.FullAccount
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.collectIsEnabledAsState
import build.wallet.feature.flags.UsSmsFeatureFlag
import build.wallet.notifications.NotificationTouchpointService
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepStateDao
import build.wallet.platform.settings.TelephonyCountryCodeProvider
import build.wallet.platform.settings.isCountry
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.RecoveryState.*
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.RecoveryState.PushNotificationsSetupUiState.OverlayState
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.RecoveryState.PushNotificationsSetupUiState.OverlayState.PushAlertState
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.RecoveryState.PushNotificationsSetupUiState.OverlayState.SystemPromptRequestingPush
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.NotCompleted
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachine
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.OnboardingAndRecovery
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiStateMachine
import build.wallet.statemachine.platform.permissions.NotificationPermissionRequester
import build.wallet.ui.model.alert.ButtonAlertModel
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class NotificationPreferencesSetupUiStateMachineImpl(
  private val accountService: AccountService,
  private val eventTracker: EventTracker,
  private val notificationPermissionRequester: NotificationPermissionRequester,
  private val notificationTouchpointService: NotificationTouchpointService,
  private val notificationPreferencesUiStateMachine: NotificationPreferencesUiStateMachine,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
  private val notificationTouchpointInputAndVerificationUiStateMachine:
    NotificationTouchpointInputAndVerificationUiStateMachine,
  private val pushItemModelProvider: RecoveryChannelsSetupPushItemModelProvider,
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider,
  private val usSmsFeatureFlag: UsSmsFeatureFlag,
) : NotificationPreferencesSetupUiStateMachine {
  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: NotificationPreferencesSetupUiProps): ScreenModel {
    val scope = rememberStableCoroutineScope()

    // Resolve the current FullAccount from AccountService (works during both onboarding and
    // active account states). Used to determine whether W3 hardware verification is needed.
    val fullAccount: FullAccount? by remember {
      accountService.accountStatus()
        .mapLatest { result ->
          val status: AccountStatus? = result.get()
          status?.let { AccountStatus.accountFromAccountStatus(it) } as? FullAccount
        }
    }.collectAsState(initial = null)

    var smsState by remember { mutableStateOf(NotCompleted) }
    var emailState by remember { mutableStateOf(NotCompleted) }

    val notificationTouchpointData =
      remember { notificationTouchpointService.notificationTouchpointData() }
        .collectAsState(initial = null).value

    var state: RecoveryState by remember { mutableStateOf(EnteringAndVerifyingEmailUiState) }

    val pushItemModel by remember {
      pushItemModelProvider.model(
        onShowAlert = { alertState ->
          state = PushNotificationsSetupUiState(
            overlayState = PushAlertState(alertState)
          )
        }
      )
    }.collectAsState()

    // Reactively sync email display state from touchpoint data.
    LaunchedEffect("email-state", notificationTouchpointData?.email) {
      val storedEmail = notificationTouchpointData?.email
      if (storedEmail != null) {
        emailState = Completed
      }
    }

    LaunchedEffect("phone-number-state", notificationTouchpointData?.phoneNumber) {
      val storedPhoneNumber = notificationTouchpointData?.phoneNumber
      if (storedPhoneNumber != null) {
        smsState = Completed
      }
    }

    // Whether SMS features are enabled for US customers via feature flag
    val usSmsEnabled by remember {
      usSmsFeatureFlag.flagValue()
    }.collectAsState()

    // SMS is not allowed in the USA unless the feature flag is enabled
    val isCountryUS = telephonyCountryCodeProvider.isCountry("us")
    val shouldShowSmsItem = !isCountryUS || usSmsEnabled.value

    // One-shot redirect for resumed onboarding. If email is already stored, skip ahead in the
    // sequential flow to the next pending step, respecting SMS availability and push completion.
    // Re-run if SMS eligibility or push completion changes so routing stays accurate if
    // feature flags update mid-session. The state guard ensures we only redirect while
    // the user is still on the email step.
    LaunchedEffect(shouldShowSmsItem, pushItemModel.state) {
      val initialData = notificationTouchpointService.notificationTouchpointData().first()
      if (initialData.email != null && state == EnteringAndVerifyingEmailUiState) {
        state = when {
          initialData.phoneNumber == null && shouldShowSmsItem -> EnteringAndVerifyingPhoneNumberUiState
          pushItemModel.state == Completed -> TransactionsAndProductUpdatesState
          else -> PushNotificationsSetupUiState()
        }
      }
    }

    // Single boundary check: gate any transition to TransactionsAndProductUpdatesState behind
    // the required email check.
    val advanceToTransactions = {
      state = if (emailState != Completed) {
        EnteringAndVerifyingEmailUiState
      } else {
        TransactionsAndProductUpdatesState
      }
    }

    // Where to go when navigating back from the push setup screen
    val backFromPush = {
      if (shouldShowSmsItem) {
        state = EnteringAndVerifyingPhoneNumberUiState
      } else {
        state = EnteringAndVerifyingEmailUiState
      }
    }

    // Auto-advance to transactions when returning from OS settings with push now enabled
    LaunchedEffect("auto-advance-after-settings", pushItemModel.state) {
      if (state is PushNotificationsSetupUiState && pushItemModel.state == Completed) {
        advanceToTransactions()
      }
    }

    return when (val currentState = state) {
      is EnteringAndVerifyingEmailUiState -> {
        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props =
            NotificationTouchpointInputAndVerificationProps(
              accountId = props.accountId,
              touchpointType = NotificationTouchpointType.Email,
              entryPoint = OnboardingAndRecovery(fullAccount = fullAccount),
              // Email is always the first screen — no back button
              onClose = null,
              onSuccess = {
                emailState = Completed
                when {
                  shouldShowSmsItem -> state = EnteringAndVerifyingPhoneNumberUiState
                  pushItemModel.state == Completed -> advanceToTransactions()
                  else -> state = PushNotificationsSetupUiState()
                }
              }
            )
        )
      }

      is EnteringAndVerifyingPhoneNumberUiState -> {
        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props =
            NotificationTouchpointInputAndVerificationProps(
              accountId = props.accountId,
              touchpointType = NotificationTouchpointType.PhoneNumber,
              entryPoint = OnboardingAndRecovery(
                fullAccount = fullAccount,
                onSkip = {
                  if (pushItemModel.state == Completed) {
                    advanceToTransactions()
                  } else {
                    state = PushNotificationsSetupUiState()
                  }
                }
              ),
              // ← back from SMS returns to email
              onClose = { state = EnteringAndVerifyingEmailUiState },
              onSuccess = {
                smsState = Completed
                if (pushItemModel.state == Completed) {
                  advanceToTransactions()
                } else {
                  state = PushNotificationsSetupUiState()
                }
              }
            )
        )
      }

      is PushNotificationsSetupUiState -> {
        // Handle system prompt overlay (special state that needs composable side-effect)
        handlePushOverlayState(
          overlayState = currentState.overlayState,
          advanceToTransactions = advanceToTransactions
        )

        RecoveryNotificationsSetupFormBodyModel(
          onAllowNotifications = {
            pushItemModel.onClick?.invoke()
          },
          onSkip = {
            eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED)
            advanceToTransactions()
          },
          onNavigateBack = backFromPush
        ).asRootScreen(
          alertModel = constructPushAlertModel(
            overlayState = currentState.overlayState,
            setState = { state = it },
            pushItemModel = pushItemModel,
            advanceToTransactions = advanceToTransactions
          )
        )
      }

      TransactionsAndProductUpdatesState -> {
        notificationPreferencesUiStateMachine.model(
          NotificationPreferencesProps(
            accountId = props.accountId,
            onboardingRecoveryChannelsEnabled = setOfNotNull(
              NotificationChannel.Push.takeIf { pushItemModel.state == Completed },
              NotificationChannel.Sms.takeIf { smsState == Completed },
              NotificationChannel.Email // Always, currently
            ),
            onBack = { state = PushNotificationsSetupUiState() },
            source = props.source,
            onComplete = {
              scope.launch {
                onboardingKeyboxStepStateDao.setStateForStep(
                  OnboardingKeyboxStep.NotificationPreferences,
                  Complete
                )
                props.onComplete()
              }
            }
          )
        )
      }
    }
  }

  /**
   * Handle push overlay states that need composable side-effects (e.g. system permission prompt).
   */
  @Composable
  private fun handlePushOverlayState(
    overlayState: OverlayState,
    advanceToTransactions: () -> Unit,
  ) {
    if (overlayState is SystemPromptRequestingPush) {
      notificationPermissionRequester.requestNotificationPermission(
        onGranted = {
          eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
          advanceToTransactions()
        },
        onDeclined = {
          eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_DISABLED)
          advanceToTransactions()
        }
      )
    }
  }

  /**
   * Build an alert model for push permission dialogs shown over the push setup screen.
   */
  private fun constructPushAlertModel(
    overlayState: OverlayState,
    setState: (RecoveryState) -> Unit,
    pushItemModel: RecoveryChannelsSetupFormItemModel,
    advanceToTransactions: () -> Unit,
  ): ButtonAlertModel? =
    when (overlayState) {
      !is PushAlertState -> null
      else -> when (overlayState.pushActionState) {
        is RecoveryChannelsSetupPushActionState.AppInfoPromptRequestingPush -> {
          requestPushAlertModel(
            onAllow = {
              setState(
                PushNotificationsSetupUiState(
                  overlayState = SystemPromptRequestingPush
                )
              )
              eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_ENABLED)
            },
            onDontAllow = {
              advanceToTransactions()
              eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED)
            }
          )
        }
        is RecoveryChannelsSetupPushActionState.OpenSettings -> {
          openSettingsForPushAlertModel(
            pushEnabled = pushItemModel.state == Completed,
            settingsOpenAction = {
              overlayState.pushActionState.openAction()
              // Return to push setup page; auto-advance LaunchedEffect will fire if push becomes enabled
              setState(PushNotificationsSetupUiState())
            },
            onClose = {
              advanceToTransactions()
            }
          )
        }
      }
    }

  private sealed interface RecoveryState {
    /** Entering email and going through the resulting verify flow. */
    data object EnteringAndVerifyingEmailUiState : RecoveryState

    /** The customer is entering and verifying their phone number. */
    data object EnteringAndVerifyingPhoneNumberUiState : RecoveryState

    /**
     * Fullscreen push notification setup page (always shown sequentially after email/SMS).
     *
     * @property overlayState Alert or system prompt shown over the push setup screen.
     */
    data class PushNotificationsSetupUiState(
      val overlayState: OverlayState = OverlayState.None,
    ) : RecoveryState {
      sealed interface OverlayState {
        data object None : OverlayState

        /** Alerts related to push permission. */
        data class PushAlertState(val pushActionState: RecoveryChannelsSetupPushActionState) :
          OverlayState

        /** The OS-level system prompt to request push notifications. */
        data object SystemPromptRequestingPush : OverlayState
      }
    }

    /** Customer is selecting notification options */
    data object TransactionsAndProductUpdatesState : RecoveryState
  }
}

const val RECOVERY_INFO_URL = "https://bitkey.world/serious-about-security"

/**
 * App dialog informing user about push request
 */
private fun requestPushAlertModel(
  onAllow: () -> Unit,
  onDontAllow: () -> Unit,
) = ButtonAlertModel(
  title = "Recovery notifications",
  subline = "Enabling push notifications for recovery verification is highly recommended and will help keep you, and your funds, safe in case you lose your Bitkey device.",
  onDismiss = onDontAllow,
  primaryButtonText = "Allow",
  onPrimaryButtonClick = onAllow,
  secondaryButtonText = "Don't allow",
  onSecondaryButtonClick = onDontAllow
)
