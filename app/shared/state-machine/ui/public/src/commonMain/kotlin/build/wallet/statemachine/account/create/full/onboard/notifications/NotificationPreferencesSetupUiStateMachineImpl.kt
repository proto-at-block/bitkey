package build.wallet.statemachine.account.create.full.onboard.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_EMAIL_INPUT_SKIP
import build.wallet.analytics.v1.Action.ACTION_APP_PHONE_NUMBER_INPUT_SKIP
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_ENABLED
import build.wallet.notifications.NotificationTouchpointDao
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepStateDao
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.NeedsAction
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.Skipped
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.State.EnteringAndVerifyingEmailUiState
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.State.EnteringAndVerifyingPhoneNumberUiState
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.State.ShowingSetupInstructionsUiState
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.State.ShowingSetupInstructionsUiState.AlertState.None
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.State.ShowingSetupInstructionsUiState.AlertState.OpenSettings
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.State.ShowingSetupInstructionsUiState.AlertState.SystemPromptRequestingPush
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Onboarding
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiStateMachine
import build.wallet.statemachine.platform.permissions.NotificationPermissionRequester
import build.wallet.ui.model.alert.AlertModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.time.Duration.Companion.seconds

class NotificationPreferencesSetupUiStateMachineImpl(
  private val eventTracker: EventTracker,
  private val notificationPermissionRequester: NotificationPermissionRequester,
  private val notificationTouchpointDao: NotificationTouchpointDao,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
  private val notificationTouchpointInputAndVerificationUiStateMachine:
    NotificationTouchpointInputAndVerificationUiStateMachine,
  private val pushItemModelProvider: NotificationPreferencesSetupPushItemModelProvider,
) : NotificationPreferencesSetupUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: NotificationPreferencesSetupUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(ShowingSetupInstructionsUiState()) }

    val pushItemModel by remember {
      pushItemModelProvider.model(
        onShowAlert = { alertState ->
          state = ShowingSetupInstructionsUiState(alertState)
        }
      )
    }.collectAsState(
      initial =
        NotificationPreferencesSetupFormItemModel(
          state = NeedsAction,
          onClick = {}
        )
    )

    var smsState by remember {
      mutableStateOf(NeedsAction)
    }
    var emailState by remember {
      mutableStateOf(NeedsAction)
    }

    LaunchedEffect("observe-stored-phone-number") {
      notificationTouchpointDao
        .phoneNumber()
        .collectLatest { storedPhoneNumber ->
          if (storedPhoneNumber != null) {
            smsState = Completed
          }
        }
    }

    LaunchedEffect("observe-stored-email") {
      notificationTouchpointDao
        .email()
        .collectLatest { storedEmail ->
          if (storedEmail != null) {
            emailState = Completed
          }
        }
    }

    return when (val currentState = state) {
      is ShowingSetupInstructionsUiState -> {
        when (currentState.alertState) {
          is None, is OpenSettings -> Unit
          is SystemPromptRequestingPush -> {
            notificationPermissionRequester.requestNotificationPermission(
              onGranted = {
                eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
                state = ShowingSetupInstructionsUiState()
              },
              onDeclined = {
                eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_DISABLED)
                state = ShowingSetupInstructionsUiState()
              }
            )
          }
        }

        LaunchedEffect(
          "complete-after-delay",
          state,
          pushItemModel,
          smsState,
          emailState
        ) {
          if (setOf<NotificationPreferencesSetupFormItemModel.State>(
              pushItemModel.state,
              smsState,
              emailState
            ).contains(NeedsAction)
          ) {
            // Don't do anything if there are any that still need action
            return@LaunchedEffect
          }

          // Otherwise, make sure either phone number or email is complete
          if (!setOf<NotificationPreferencesSetupFormItemModel.State>(
              smsState,
              emailState
            ).contains(Completed)
          ) {
            // Don't do anything if there's not one completed
            return@LaunchedEffect
          }

          // Complete!
          delay(1.seconds)
          // This will transition the UI
          onboardingKeyboxStepStateDao
            .setStateForStep(NotificationPreferences, Complete)
          props.onComplete()
        }

        NotificationPreferencesSetupFormBodyModel(
          pushItem = pushItemModel,
          smsItem =
            NotificationPreferencesSetupFormItemModel(
              state = smsState,
              onClick =
                when (smsState) {
                  Completed -> null
                  else -> {
                    { state = EnteringAndVerifyingPhoneNumberUiState }
                  }
                }
            ),
          emailItem =
            NotificationPreferencesSetupFormItemModel(
              state = emailState,
              onClick =
                when (emailState) {
                  Completed -> null
                  else -> {
                    { state = EnteringAndVerifyingEmailUiState }
                  }
                }
            ),
          alertModel =
            when (currentState.alertState) {
              is None, is SystemPromptRequestingPush -> null
              is OpenSettings -> {
                AlertModel(
                  title = "Open Settings to enable push notifications",
                  subline = "",
                  primaryButtonText = "Settings",
                  secondaryButtonText = "Close",
                  onDismiss = { state = ShowingSetupInstructionsUiState() },
                  onPrimaryButtonClick = { currentState.alertState.openAction.invoke() },
                  onSecondaryButtonClick = { state = ShowingSetupInstructionsUiState() }
                )
              }
            }
        )
      }

      is EnteringAndVerifyingPhoneNumberUiState -> {
        val skipAction = {
          eventTracker.track(ACTION_APP_PHONE_NUMBER_INPUT_SKIP)
          smsState = Skipped
          state = ShowingSetupInstructionsUiState()
        }

        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props =
            NotificationTouchpointInputAndVerificationProps(
              fullAccountId = props.fullAccountId,
              keyboxConfig = props.keyboxConfig,
              touchpointType = NotificationTouchpointType.PhoneNumber,
              entryPoint =
                Onboarding(
                  // Only allow skip if customer hasn't already skipped email.
                  onSkip = if (emailState == Skipped) null else skipAction,
                  skipBottomSheetProvider = { onBack ->
                    if (emailState == Skipped) {
                      // Customer already skipped email. Skipping phone number is not allowed.
                      SkipNotAllowedSheetModel(
                        enterOtherContactButtonText = "Enter email",
                        onGoBack = onBack,
                        onEnterOtherContact = {
                          state = EnteringAndVerifyingEmailUiState
                        }
                      )
                    } else {
                      // Otherwise, show the normal skip sheet for phone
                      PhoneNumberInputSkipAllowedSheetModel(
                        onGoBack = onBack,
                        onSkip = skipAction
                      )
                    }
                  }
                ),
              onClose = {
                state = ShowingSetupInstructionsUiState()
              }
            )
        )
      }

      EnteringAndVerifyingEmailUiState -> {
        val skipAction = {
          eventTracker.track(ACTION_APP_EMAIL_INPUT_SKIP)
          emailState = Skipped
          state = ShowingSetupInstructionsUiState()
        }

        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props =
            NotificationTouchpointInputAndVerificationProps(
              fullAccountId = props.fullAccountId,
              keyboxConfig = props.keyboxConfig,
              touchpointType = NotificationTouchpointType.Email,
              entryPoint =
                Onboarding(
                  // Only allow skip if customer hasn't already skipped email.
                  onSkip = if (smsState == Skipped) null else skipAction,
                  skipBottomSheetProvider = { onBack ->
                    if (smsState == Skipped) {
                      // Customer already skipped sms. Skipping email is not allowed.
                      SkipNotAllowedSheetModel(
                        enterOtherContactButtonText = "Enter phone number",
                        onGoBack = onBack,
                        onEnterOtherContact = {
                          state = EnteringAndVerifyingPhoneNumberUiState
                        }
                      )
                    } else {
                      // Otherwise, show the normal skip sheet for email
                      EmailInputSkipAllowedSheetModel(
                        onGoBack = onBack,
                        onSkip = skipAction
                      )
                    }
                  }
                ),
              onClose = {
                state = ShowingSetupInstructionsUiState()
              }
            )
        )
      }
    }
  }

  sealed interface State {
    /**
     * The setup instructions are being shown to the customer.
     */
    data class ShowingSetupInstructionsUiState(
      val alertState: AlertState = None,
    ) : State {
      sealed interface AlertState {
        /** No alert */
        data object None : AlertState

        /** The system prompt to request push notifications */
        data object SystemPromptRequestingPush : AlertState

        /**
         * The iOS-specific alert to enable push notification permissions via opening settings
         * after initially denying them.
         * @param openAction: The action to open the app-specific settings on iOS
         */
        data class OpenSettings(val openAction: () -> Unit) : AlertState
      }
    }

    /** The customer is entering and verifying their phone number. */
    data object EnteringAndVerifyingPhoneNumberUiState : State

    /** Entering email and going through the resulting verify flow. */
    data object EnteringAndVerifyingEmailUiState : State
  }
}
