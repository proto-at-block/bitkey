package build.wallet.statemachine.account.create.full

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_FAILED
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.SAVE_CLOUD_BACKUP_LOADING
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId.SAVE_NOTIFICATIONS_LOADING
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.onboarding.OnboardAccountService
import build.wallet.onboarding.OnboardAccountStep
import build.wallet.onboarding.OnboardAccountStep.CloudBackup
import build.wallet.onboarding.OnboardAccountStep.NotificationPreferences
import build.wallet.statemachine.account.create.full.OnboardFullAccountUiStateMachineImpl.State.*
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiProps
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachine
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.notifications.NotificationPreferencesProps.Source.Onboarding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class OnboardFullAccountUiStateMachineImpl(
  private val onboardAccountService: OnboardAccountService,
  private val fullAccountCloudSignInAndBackupUiStateMachine:
    FullAccountCloudSignInAndBackupUiStateMachine,
  private val notificationPreferencesSetupUiStateMachine:
    NotificationPreferencesSetupUiStateMachine,
) : OnboardFullAccountUiStateMachine {
  @Suppress("CyclomaticComplexMethod")
  @Composable
  override fun model(props: OnboardFullAccountUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(LoadingOnboardingState) }

    return when (val currentState = state) {
      is LoadingOnboardingState -> {
        LaunchedEffect("load-onboarding-state") {
          onboardAccountService.pendingStep()
            .onSuccess { step ->
              when (step) {
                null -> props.onOnboardingComplete()
                else -> state = HandlingOnboardingStep(step)
              }
            }
            .onFailure {
              state = ErrorLoadingOnboardingState(it)
            }
        }
        LoadingBodyModel(id = LOADING_ONBOARDING_STEP).asRootScreen()
      }
      is ErrorLoadingOnboardingState -> {
        ErrorFormBodyModel(
          title = "Error completing onboarding",
          subline = "Please retry.",
          primaryButton = ButtonDataModel(
            text = "Retry",
            onClick = {
              state = LoadingOnboardingState
            }
          ),
          eventTrackerScreenId = null
        ).asRootScreen()
      }
      is HandlingOnboardingStep -> {
        when (currentState.step) {
          is CloudBackup -> {
            fullAccountCloudSignInAndBackupUiStateMachine.model(
              props = FullAccountCloudSignInAndBackupProps(
                sealedCsek = currentState.step.sealedCsek,
                keybox = props.keybox,
                onBackupFailed = {
                  state = ErrorHandlingOnboardingStep(
                    step = currentState.step,
                    error = it
                      ?: Error("Error completing onboarding step ${currentState.step::class.simpleName}")
                  )
                },
                onBackupSaved = {
                  state = CompletingOnboardingStep(currentState.step)
                },
                onExistingAppDataFound = { cloudBackup, proceed ->
                  if (cloudBackup?.accountId == props.keybox.fullAccountId.serverId) {
                    // It's OK to overwrite the backup if it's for the same account
                    proceed()
                  } else if (cloudBackup !is CloudBackupV2 || cloudBackup.fullAccountFields != null) {
                    if (props.isSkipCloudBackupInstructions) {
                      proceed()
                    } else {
                      props.onOverwriteFullAccountCloudBackupWarning()
                    }
                  } else {
                    props.onFoundLiteAccountWithDifferentId(cloudBackup)
                  }
                },
                presentationStyle = ScreenPresentationStyle.Root,
                isSkipCloudBackupInstructions = props.isSkipCloudBackupInstructions,
                requireAuthRefreshForCloudBackup = true
              )
            )
          }
          is NotificationPreferences -> {
            notificationPreferencesSetupUiStateMachine.model(
              props = NotificationPreferencesSetupUiProps(
                accountId = props.keybox.fullAccountId,
                accountConfig = props.keybox.config,
                source = Onboarding,
                onComplete = {
                  state = CompletingOnboardingStep(currentState.step)
                }
              )
            )
          }
        }
      }
      is ErrorHandlingOnboardingStep -> {
        when (currentState.step) {
          is CloudBackup -> {
            CloudBackupFailedScreenModel(
              eventTrackerScreenId = SAVE_CLOUD_BACKUP_FAILED,
              error = currentState.error,
              onTryAgain = {
                state = HandlingOnboardingStep(step = currentState.step)
              }
            ).asRootScreen()
          }
          is NotificationPreferences -> {
            ErrorFormBodyModel(
              title = "Error setting up notifications",
              subline = "Please retry.",
              primaryButton = ButtonDataModel(
                text = "Retry",
                onClick = {
                  state = HandlingOnboardingStep(step = currentState.step)
                }
              ),
              eventTrackerScreenId = null
            ).asRootScreen()
          }
        }
      }
      is CompletingOnboardingStep -> {
        LaunchedEffect("complete-onboarding-step") {
          onboardAccountService.completeStep(currentState.step)
            .onSuccess {
              onboardAccountService.pendingStep()
                .onSuccess { step ->
                  when (step) {
                    null -> props.onOnboardingComplete()
                    else -> state = HandlingOnboardingStep(step)
                  }
                }
                .onFailure {
                  state = ErrorCompletingOnboardingStep(
                    error = it,
                    step = currentState.step
                  )
                }
            }
            .onFailure {
              state = ErrorCompletingOnboardingStep(
                error = it,
                step = currentState.step
              )
            }
        }
        val screenId = when (currentState.step) {
          is CloudBackup -> SAVE_CLOUD_BACKUP_LOADING
          is NotificationPreferences -> SAVE_NOTIFICATIONS_LOADING
        }
        LoadingBodyModel(id = screenId).asRootScreen()
      }
      is ErrorCompletingOnboardingStep -> {
        ErrorFormBodyModel(
          title = "Error completing onboarding",
          subline = "Please retry.",
          primaryButton = ButtonDataModel(
            text = "Retry",
            onClick = {
              state = CompletingOnboardingStep(currentState.step)
            }
          ),
          eventTrackerScreenId = null
        ).asRootScreen()
      }
    }
  }

  private sealed interface State {
    /**
     * Indicates that we are loading initial onboarding step for customer to complete.
     */
    data object LoadingOnboardingState : State

    /**
     * Indicates that an error occurred while loading initial onboarding step.
     */
    data class ErrorLoadingOnboardingState(
      val error: Throwable,
    ) : State

    /**
     * Indicates that customer is currently handling an onboarding step.
     */
    data class HandlingOnboardingStep(
      val step: OnboardAccountStep,
    ) : State

    /**
     * Indicates that an error occurred while handling an onboarding step.
     */
    data class ErrorHandlingOnboardingStep(
      val error: Throwable,
      val step: OnboardAccountStep,
    ) : State

    /**
     * Indicates that we are marking the onboarding step as complete.
     */
    data class CompletingOnboardingStep(
      val step: OnboardAccountStep,
    ) : State

    /**
     * Indicates that an error occurred while marking the onboarding step as complete.
     */
    data class ErrorCompletingOnboardingStep(
      val error: Throwable,
      val step: OnboardAccountStep,
    ) : State
  }
}
