package build.wallet.statemachine.account.create.full.onboard.notifications

import androidx.compose.runtime.*
import bitkey.notifications.NotificationChannel
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.*
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.UsSmsFeatureFlag
import build.wallet.feature.flags.W3OnboardingFeatureFlag
import build.wallet.notifications.NotificationTouchpointService
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.onboarding.OnboardingKeyboxStep
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepStateDao
import build.wallet.platform.settings.TelephonyCountryCodeProvider
import build.wallet.platform.settings.isCountry
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.RecoveryState.*
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.RecoveryState.ConfigureRecoveryOptionsUiState.*
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.RecoveryState.ConfigureRecoveryOptionsUiState.BottomSheetState.ConfirmSkipRecoveryMethods
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.RecoveryState.ConfigureRecoveryOptionsUiState.BottomSheetState.NoEmailError
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.RecoveryState.ConfigureRecoveryOptionsUiState.OverlayState.None
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.RecoveryState.ConfigureRecoveryOptionsUiState.SpecialState.SystemPromptRequestingPush
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.NotCompleted
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachine
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Recovery
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiStateMachine
import build.wallet.statemachine.platform.permissions.NotificationPermissionRequester
import build.wallet.ui.model.alert.ButtonAlertModel
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class NotificationPreferencesSetupUiStateMachineImpl(
  private val eventTracker: EventTracker,
  private val notificationPermissionRequester: NotificationPermissionRequester,
  private val notificationTouchpointService: NotificationTouchpointService,
  private val notificationPreferencesUiStateMachine: NotificationPreferencesUiStateMachine,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
  private val notificationTouchpointInputAndVerificationUiStateMachine:
    NotificationTouchpointInputAndVerificationUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val pushItemModelProvider: RecoveryChannelsSetupPushItemModelProvider,
  private val telephonyCountryCodeProvider: TelephonyCountryCodeProvider,
  private val uiErrorHintsProvider: UiErrorHintsProvider,
  private val usSmsFeatureFlag: UsSmsFeatureFlag,
  private val w3OnboardingFeatureFlag: W3OnboardingFeatureFlag,
) : NotificationPreferencesSetupUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: NotificationPreferencesSetupUiProps): ScreenModel {
    var state: RecoveryState by remember { mutableStateOf(ConfigureRecoveryOptionsUiState()) }
    val scope = rememberStableCoroutineScope()
    val smsErrorHint = uiErrorHintsProvider.errorHintFlow(UiErrorHintKey.Phone).collectAsState()

    val pushItemModel by remember {
      pushItemModelProvider.model(
        onShowAlert = { alertState ->
          // Determine if we should advance to transactions after push completes
          val shouldAdvance = when (val currentState = state) {
            is ConfigureRecoveryOptionsUiState -> currentState.advanceToTransactionsAfterPush
            // Coming from fullscreen page - should advance after push
            is RecoveryState.RecoveryNotificationsSetupUiState -> true
            else -> false
          }
          state = ConfigureRecoveryOptionsUiState(
            overlayState = PushAlertState(alertState),
            advanceToTransactionsAfterPush = shouldAdvance
          )
        }
      )
    }.collectAsState()

    var smsState by remember {
      mutableStateOf(NotCompleted)
    }
    var smsNumber by remember {
      mutableStateOf<String?>(null)
    }
    var emailState by remember {
      mutableStateOf(NotCompleted)
    }
    var emailAddress by remember {
      mutableStateOf<String?>(null)
    }

    val notificationTouchpointData =
      remember { notificationTouchpointService.notificationTouchpointData() }
        .collectAsState(initial = null).value

    LaunchedEffect("email-state", notificationTouchpointData?.email) {
      val storedEmail = notificationTouchpointData?.email
      if (storedEmail != null) {
        emailState = Completed
        emailAddress = storedEmail.value
      }
    }

    LaunchedEffect("phone-number-state", notificationTouchpointData?.phoneNumber) {
      val storedPhoneNumber = notificationTouchpointData?.phoneNumber
      if (storedPhoneNumber != null) {
        smsState = Completed
        smsNumber = storedPhoneNumber.formattedDisplayValue
      }
    }

    // Whether SMS features are enabled for US customers via feature flag
    val usSmsEnabled by remember {
      usSmsFeatureFlag.flagValue()
    }.collectAsState()

    // Whether W3 onboarding flow is enabled
    val w3OnboardingEnabled by remember {
      w3OnboardingFeatureFlag.flagValue()
    }.collectAsState()

    // SMS is not allowed in the USA unless the feature flag is enabled
    val isCountryUS = telephonyCountryCodeProvider.isCountry("us")
    val shouldShowSmsItem = !isCountryUS || usSmsEnabled.value

    // When feature flag is ON and push is not completed, intercept push click to show fullscreen page
    val effectivePushItemModel = if (w3OnboardingEnabled.value && pushItemModel.state != Completed) {
      pushItemModel.copy(
        onClick = { state = RecoveryState.RecoveryNotificationsSetupUiState }
      )
    } else {
      pushItemModel
    }

    // Auto-advance to transactions when returning from settings with push now enabled
    // This handles the case where user opened app settings and enabled notifications
    LaunchedEffect("auto-advance-after-settings", pushItemModel.state) {
      val currentState = state
      if (currentState is ConfigureRecoveryOptionsUiState &&
        currentState.advanceToTransactionsAfterPush &&
        pushItemModel.state == Completed
      ) {
        state = TransactionsAndProductUpdatesState
      }
    }

    return when (val currentState = state) {
      is ConfigureRecoveryOptionsUiState -> {
        // Deal with some special states outside of the ScreenModel domain
        handleSpecialOverlayState(
          overlayState = currentState.overlayState,
          advanceToTransactionsAfterPush = currentState.advanceToTransactionsAfterPush,
          setState = { state = it }
        )

        RecoveryChannelsSetupFormBodyModel(
          pushItem = effectivePushItemModel,
          smsItem = RecoveryChannelsSetupFormItemModel(
            state = smsState,
            displayValue = smsNumber,
            uiErrorHint = smsErrorHint.value,
            onClick =
              when (smsState) {
                Completed -> null
                else -> {
                  { state = EnteringAndVerifyingPhoneNumberUiState }
                }
              }
          ).takeIf { shouldShowSmsItem },
          emailItem =
            RecoveryChannelsSetupFormItemModel(
              state = emailState,
              displayValue = emailAddress,
              uiErrorHint = UiErrorHint.None,
              onClick =
                when (emailState) {
                  Completed -> null
                  else -> {
                    { state = EnteringAndVerifyingEmailUiState }
                  }
                }
            ),
          continueOnClick = {
            val allOptionsCompleted =
              (!shouldShowSmsItem || smsState == Completed || smsErrorHint.value != UiErrorHint.None) &&
                emailState == Completed &&
                pushItemModel.state == Completed

            state = if (allOptionsCompleted) {
              TransactionsAndProductUpdatesState
            } else if (emailState != Completed) {
              ConfigureRecoveryOptionsUiState(overlayState = NoEmailError)
            } else {
              ConfigureRecoveryOptionsUiState(overlayState = ConfirmSkipRecoveryMethods)
            }
          },
          learnOnClick = {
            if ((state as? ConfigureRecoveryOptionsUiState)?.overlayState == None) {
              state = RecoveryState.ShowLearnRecoveryWebView
            }
          }
        ).asRootScreen(
          alertModel = constructAlertModel(
            overlayState = currentState.overlayState,
            advanceToTransactionsAfterPush = currentState.advanceToTransactionsAfterPush,
            setState = { state = it },
            pushItemModel = pushItemModel
          ),
          bottomSheetModel = constructBottomSheetModel(
            currentState.overlayState,
            setState = { state = it }
          )
        )
      }

      is EnteringAndVerifyingPhoneNumberUiState -> {
        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props =
            NotificationTouchpointInputAndVerificationProps(
              accountId = props.accountId,
              touchpointType = NotificationTouchpointType.PhoneNumber,
              // Only show skip button in sequential flow (feature flag ON)
              entryPoint = Recovery(
                onSkip = if (w3OnboardingEnabled.value) {
                  {
                    // Skip advances to push setup page or transactions (not back to hub)
                    if (pushItemModel.state == Completed) {
                      state = TransactionsAndProductUpdatesState
                    } else {
                      state = RecoveryState.RecoveryNotificationsSetupUiState
                    }
                  }
                } else {
                  null
                }
              ),
              onClose = { state = ConfigureRecoveryOptionsUiState() },
              onSuccess = {
                if (w3OnboardingEnabled.value) {
                  // Sequential flow: advance to push fullscreen page or transactions
                  if (pushItemModel.state == Completed) {
                    state = TransactionsAndProductUpdatesState
                  } else {
                    // Show fullscreen push notification setup page
                    state = RecoveryState.RecoveryNotificationsSetupUiState
                  }
                } else {
                  // Hub-and-spoke: return to hub
                  state = ConfigureRecoveryOptionsUiState()
                }
              }
            )
        )
      }

      EnteringAndVerifyingEmailUiState -> {
        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props =
            NotificationTouchpointInputAndVerificationProps(
              accountId = props.accountId,
              touchpointType = NotificationTouchpointType.Email,
              entryPoint = Recovery(),
              onClose = { state = ConfigureRecoveryOptionsUiState() },
              onSuccess = {
                if (w3OnboardingEnabled.value) {
                  // Sequential flow: advance to next channel
                  when {
                    shouldShowSmsItem -> state = EnteringAndVerifyingPhoneNumberUiState
                    pushItemModel.state == Completed -> state = TransactionsAndProductUpdatesState
                    else -> {
                      // Show fullscreen push notification setup page
                      state = RecoveryState.RecoveryNotificationsSetupUiState
                    }
                  }
                } else {
                  // Hub-and-spoke: return to hub
                  state = ConfigureRecoveryOptionsUiState()
                }
              }
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
            onBack = { state = ConfigureRecoveryOptionsUiState() },
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

      RecoveryState.ShowLearnRecoveryWebView -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = RECOVERY_INFO_URL,
              onClose = { state = ConfigureRecoveryOptionsUiState() }
            )
          }
        ).asModalScreen()
      }

      RecoveryState.RecoveryNotificationsSetupUiState -> {
        RecoveryNotificationsSetupFormBodyModel(
          onAllowNotifications = {
            // Use the original push item's onClick to trigger proper permission check
            // This will call onShowAlert which handles both NotDetermined (show prompt)
            // and Denied (open settings) cases consistently with the hub dialog
            pushItemModel.onClick?.invoke()
          },
          onSkip = {
            eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED)
            state = TransactionsAndProductUpdatesState
          },
          onClose = {
            eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED)
            // Go back to hub
            state = ConfigureRecoveryOptionsUiState()
          }
        ).asModalScreen()
      }
    }
  }

  /**
   * Special overlays don't really fit neatly into our screen model and need special
   * handling.
   *
   * @param advanceToTransactionsAfterPush When true, after push permission is granted or declined,
   * advance to TransactionsAndProductUpdatesState instead of returning to the hub.
   */
  @Composable
  private fun handleSpecialOverlayState(
    overlayState: OverlayState,
    advanceToTransactionsAfterPush: Boolean,
    setState: (RecoveryState) -> Unit,
  ) {
    when (overlayState) {
      !is SpecialState -> Unit
      is SystemPromptRequestingPush -> {
        notificationPermissionRequester.requestNotificationPermission(
          onGranted = {
            eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
            setState(
              if (advanceToTransactionsAfterPush) {
                TransactionsAndProductUpdatesState
              } else {
                ConfigureRecoveryOptionsUiState()
              }
            )
          },
          onDeclined = {
            eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_DISABLED)
            setState(
              if (advanceToTransactionsAfterPush) {
                TransactionsAndProductUpdatesState
              } else {
                ConfigureRecoveryOptionsUiState()
              }
            )
          }
        )
      }
    }
  }

  /**
   * Create an alert model for the main screen
   *
   * @param advanceToTransactionsAfterPush When true, after push notification setup completes
   * (whether user allows or denies), advance to TransactionsAndProductUpdatesState.
   */
  private fun constructAlertModel(
    overlayState: OverlayState,
    advanceToTransactionsAfterPush: Boolean,
    setState: (RecoveryState) -> Unit,
    pushItemModel: RecoveryChannelsSetupFormItemModel,
  ): ButtonAlertModel? =
    when (overlayState) {
      !is PushAlertState -> null
      else -> when (overlayState.pushActionState) {
        is RecoveryChannelsSetupPushActionState.AppInfoPromptRequestingPush -> {
          requestPushAlertModel(
            onAllow = {
              // Preserve advanceToTransactionsAfterPush when transitioning to system prompt
              setState(
                ConfigureRecoveryOptionsUiState(
                  overlayState = SystemPromptRequestingPush,
                  advanceToTransactionsAfterPush = advanceToTransactionsAfterPush
                )
              )
              eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_ENABLED)
            },
            onDontAllow = {
              // User declined at app dialog - respect the sequential flow flag
              setState(
                if (advanceToTransactionsAfterPush) {
                  TransactionsAndProductUpdatesState
                } else {
                  ConfigureRecoveryOptionsUiState()
                }
              )
              eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_BITKEY_DISABLED)
            }
          )
        }
        is RecoveryChannelsSetupPushActionState.OpenSettings -> {
          openSettingsForPushAlertModel(
            pushEnabled = pushItemModel.state == Completed,
            settingsOpenAction = {
              overlayState.pushActionState.openAction()
              // After opening settings, return to hub with advanceToTransactionsAfterPush preserved
              // The LaunchedEffect will auto-advance if push becomes enabled
              setState(
                ConfigureRecoveryOptionsUiState(
                  advanceToTransactionsAfterPush = advanceToTransactionsAfterPush
                )
              )
            },
            onClose = {
              // User dismissed without opening settings - respect sequential flow
              setState(
                if (advanceToTransactionsAfterPush) {
                  TransactionsAndProductUpdatesState
                } else {
                  ConfigureRecoveryOptionsUiState()
                }
              )
            }
          )
        }
      }
    }

  /**
   * Create a bottom sheet for the main screen
   */
  private fun constructBottomSheetModel(
    overlayState: OverlayState,
    setState: (RecoveryState) -> Unit,
  ): SheetModel? =
    when (overlayState) {
      !is BottomSheetState -> null
      NoEmailError -> EmailRecoveryMethodRequiredErrorModal(
        onCancel = { setState(ConfigureRecoveryOptionsUiState()) }
      ).asSheetModalScreen {
        setState(ConfigureRecoveryOptionsUiState())
      }
      ConfirmSkipRecoveryMethods -> ConfirmSkipRecoveryMethodsSheetModel(
        onCancel = { setState(ConfigureRecoveryOptionsUiState()) },
        onContinue = { setState(TransactionsAndProductUpdatesState) }
      ).asSheetModalScreen {
        setState(TransactionsAndProductUpdatesState)
      }
    }

  private sealed interface RecoveryState {
    /**
     * Recovery options and status shown to user
     *
     * @property advanceToTransactionsAfterPush When true, after push notification setup completes
     * (whether granted or denied), automatically advance to TransactionsAndProductUpdatesState
     * instead of staying on the hub. This is used for sequential flow when feature flag is enabled.
     */
    data class ConfigureRecoveryOptionsUiState(
      val overlayState: OverlayState = None,
      val advanceToTransactionsAfterPush: Boolean = false,
    ) : RecoveryState {
      sealed interface OverlayState {
        /** No overlaid info */
        data object None : OverlayState
      }

      /**
       * Showing BottomSheet instances
       */
      sealed interface BottomSheetState : OverlayState {
        /**
         * Email required for account setup before proceeding
         */
        data object NoEmailError : BottomSheetState

        /**
         * Prompt user to set up all available recovery methods, but allow them to continue if
         * minimum (email) added
         */
        data object ConfirmSkipRecoveryMethods : BottomSheetState
      }

      /**
       * Alerts related to push actions. Should result in AlertModel, but eventually should be
       * an app-specific dialog
       */
      data class PushAlertState(val pushActionState: RecoveryChannelsSetupPushActionState) :
        OverlayState

      /**
       * Showing something that needs special handling
       */
      sealed interface SpecialState : OverlayState {
        /** The system prompt to request push notifications */
        data object SystemPromptRequestingPush : SpecialState
      }
    }

    /** The customer is entering and verifying their phone number. */
    data object EnteringAndVerifyingPhoneNumberUiState : RecoveryState

    /** Entering email and going through the resulting verify flow. */
    data object EnteringAndVerifyingEmailUiState : RecoveryState

    /** Customer is selecting notification options */
    data object TransactionsAndProductUpdatesState : RecoveryState

    /** Show recovery info web page */
    data object ShowLearnRecoveryWebView : RecoveryState

    /** Fullscreen page asking the customer to enable push notifications (sequential flow only) */
    data object RecoveryNotificationsSetupUiState : RecoveryState
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
