package build.wallet.statemachine.data.account.create.activate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.compose.collections.buildImmutableList
import build.wallet.f8e.onboarding.OnboardingService
import build.wallet.feature.isEnabled
import build.wallet.fingerprints.MultipleFingerprintsIsEnabledFeatureFlag
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import build.wallet.keybox.KeyboxDao
import build.wallet.keybox.keys.OnboardingAppKeyKeystore
import build.wallet.ktor.result.HttpError
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.onboarding.OnboardingKeyboxHardwareKeysDao
import build.wallet.onboarding.OnboardingKeyboxStepStateDao
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.ActivateKeyboxDataFull.ActivatingKeyboxDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.ActivateKeyboxDataFull.FailedToActivateKeyboxDataFull
import build.wallet.statemachine.data.account.create.activate.State.ActivatingKeyboxState
import build.wallet.statemachine.data.account.create.activate.State.FailedToActivateKeyboxState
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class ActivateFullAccountDataStateMachineImpl(
  private val eventTracker: EventTracker,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val keyboxDao: KeyboxDao,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
  private val onboardingService: OnboardingService,
  private val onboardingAppKeyKeystore: OnboardingAppKeyKeystore,
  private val onboardingKeyboxHardwareKeysDao: OnboardingKeyboxHardwareKeysDao,
  private val multipleFingerprintsIsEnabled: MultipleFingerprintsIsEnabledFeatureFlag,
) : ActivateFullAccountDataStateMachine {
  @Composable
  override fun model(
    props: ActivateFullAccountDataProps,
  ): CreateFullAccountData.ActivateKeyboxDataFull {
    var dataState: State by remember { mutableStateOf(ActivatingKeyboxState) }

    return when (val state = dataState) {
      is ActivatingKeyboxState -> {
        CompleteOnboardingAndTransitionKeyboxToActiveEffect(
          props = props,
          onFailure = { isConnectivityError ->
            dataState = FailedToActivateKeyboxState(isConnectivityError)
          }
        )
        ActivatingKeyboxDataFull
      }

      is FailedToActivateKeyboxState ->
        FailedToActivateKeyboxDataFull(
          isConnectivityError = state.isConnectivityError,
          retry = {
            dataState = ActivatingKeyboxState
          },
          onDeleteKeyboxAndExitOnboarding = props.onDeleteKeyboxAndExitOnboarding
        )
    }
  }

  @Composable
  private fun CompleteOnboardingAndTransitionKeyboxToActiveEffect(
    props: ActivateFullAccountDataProps,
    onFailure: (isConnectivityError: Boolean) -> Unit,
  ) {
    // Perform the activation
    LaunchedEffect("save-keybox") {
      // clear the app keys persisted specifically for onboarding upon completion since the account
      // and keybox are fully created
      onboardingAppKeyKeystore.clear()

      // clear the hw auth public key that was stored for upgrading a lite account backup
      onboardingKeyboxHardwareKeysDao.clear()

      // Tell the server that onboarding has been completed.
      onboardingService.completeOnboarding(
        f8eEnvironment = props.keybox.config.f8eEnvironment,
        fullAccountId = props.keybox.fullAccountId
      )
        .onFailure { error ->
          onFailure(error is HttpError.NetworkError)
        }
        .onSuccess {
          // Add getting started tasks for the new keybox
          val gettingStartedTasks =
            buildImmutableList {
              add(
                GettingStartedTask(
                  GettingStartedTask.TaskId.AddBitcoin,
                  GettingStartedTask.TaskState.Incomplete
                )
              )

              add(
                GettingStartedTask(
                  GettingStartedTask.TaskId.InviteTrustedContact,
                  GettingStartedTask.TaskState.Incomplete
                )
              )

              add(
                GettingStartedTask(
                  GettingStartedTask.TaskId.EnableSpendingLimit,
                  GettingStartedTask.TaskState.Incomplete
                )
              )

              if (multipleFingerprintsIsEnabled.isEnabled()) {
                add(
                  GettingStartedTask(
                    GettingStartedTask.TaskId.AddAdditionalFingerprint,
                    GettingStartedTask.TaskState.Incomplete
                  )
                )
              }
            }

          gettingStartedTaskDao.addTasks(gettingStartedTasks)
            .onSuccess {
              eventTracker.track(Action.ACTION_APP_GETTINGSTARTED_INITIATED)
              log { "Added getting started tasks $gettingStartedTasks" }
            }
            .logFailure { "Failed to add getting started tasks $gettingStartedTasks" }

          // Log that the account has been created
          eventTracker.track(action = Action.ACTION_APP_ACCOUNT_CREATED)

          // Set as active. This will transition the UI.
          keyboxDao.activateNewKeyboxAndCompleteOnboarding(props.keybox)
            .onSuccess {
              // Now that we have an active keybox we can clear the temporary onboarding dao
              onboardingKeyboxStepStateDao.clear()
            }
            .onFailure {
              onFailure(false)
            }
        }
    }
  }
}

private sealed interface State {
  data object ActivatingKeyboxState : State

  data class FailedToActivateKeyboxState(
    val isConnectivityError: Boolean,
  ) : State
}
