package build.wallet.statemachine.data.account.create

import androidx.compose.runtime.*
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.keybox.AppDataDeleter
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.onboarding.OnboardingKeyboxStepState
import build.wallet.onboarding.OnboardingKeyboxStepStateDao
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.ReplaceWithLiteAccountRestoreData
import build.wallet.statemachine.data.account.create.CreateAccountState.*
import build.wallet.statemachine.data.account.create.activate.ActivateFullAccountDataProps
import build.wallet.statemachine.data.account.create.activate.ActivateFullAccountDataStateMachine
import build.wallet.statemachine.data.account.create.keybox.CreateKeyboxDataProps
import build.wallet.statemachine.data.account.create.keybox.CreateKeyboxDataStateMachine
import build.wallet.statemachine.data.account.create.onboard.OnboardKeyboxDataProps
import build.wallet.statemachine.data.account.create.onboard.OnboardKeyboxDataStateMachine
import kotlinx.coroutines.launch

class CreateFullAccountDataStateMachineImpl(
  private val activateFullAccountDataStateMachine: ActivateFullAccountDataStateMachine,
  private val createKeyboxDataStateMachine: CreateKeyboxDataStateMachine,
  private val onboardKeyboxDataStateMachine: OnboardKeyboxDataStateMachine,
  private val appDataDeleter: AppDataDeleter,
  private val onboardingKeyboxStepStateDao: OnboardingKeyboxStepStateDao,
) : CreateFullAccountDataStateMachine {
  @Suppress("NestedBlockDepth")
  @Composable
  override fun model(props: CreateFullAccountDataProps): CreateFullAccountData {
    val scope = rememberStableCoroutineScope()

    // Set the initial state based on persisted onboarding keybox.
    var dataState: CreateAccountState by remember {
      mutableStateOf(
        when (props.onboardingKeybox) {
          null -> CreateKeyboxState
          else ->
            OnboardKeyboxState(
              keybox = props.onboardingKeybox,
              isSkipCloudBackupInstructions = false,
              ignoreExistingFullAccountCloudBackupFound = false
            )
        }
      )
    }

    return when (val state = dataState) {
      is CreateKeyboxState -> {
        // When the onboarding keybox changes to nonnull, we know we need to transition
        // to [OnboardKeyboxState]
        LaunchedEffect("transition-to-onboard", props.onboardingKeybox) {
          if (props.onboardingKeybox != null) {
            dataState =
              OnboardKeyboxState(
                keybox = props.onboardingKeybox,
                isSkipCloudBackupInstructions = false,
                ignoreExistingFullAccountCloudBackupFound = false
              )
          }
        }
        createKeyboxDataStateMachine.model(
          props = CreateKeyboxDataProps(
            onboardingKeybox = props.onboardingKeybox,
            context = props.context,
            rollback = props.rollback
          )
        )
      }

      is OnboardKeyboxState -> {
        TransitionToActivateStepWhenOnboardCompleteEffect(
          transitionToActivateStep = {
            dataState = ActivateKeyboxState(keybox = state.keybox)
          }
        )
        onboardKeyboxDataStateMachine.model(
          props =
            OnboardKeyboxDataProps(
              keybox = state.keybox,
              isSkipCloudBackupInstructions = state.isSkipCloudBackupInstructions,
              onExistingAppDataFound = { cloudBackup, proceed ->
                if (cloudBackup?.accountId == state.keybox.fullAccountId.serverId) {
                  // It's OK to overwrite the backup if it's for the same account
                  proceed()
                } else if (cloudBackup !is CloudBackupV2 || cloudBackup.fullAccountFields != null) {
                  if (state.isSkipCloudBackupInstructions) {
                    proceed()
                  } else {
                    dataState =
                      OverwriteFullAccountCloudBackupWarningState(
                        keybox = state.keybox,
                        onOverwrite = {
                          dataState =
                            OnboardKeyboxState(
                              keybox = state.keybox,
                              isSkipCloudBackupInstructions = true,
                              ignoreExistingFullAccountCloudBackupFound = true
                            )
                        },
                        rollback = props.rollback
                      )
                  }
                } else {
                  // Found a Lite Account with a different account ID. Upgrade the Lite Account
                  // instead to replace this full account in order to preserve the protected
                  // customers.
                  dataState =
                    ReplaceWithLiteAccountRestoreState(
                      cloudBackupV2 = cloudBackup,
                      keybox = state.keybox
                    )
                }
              }
            )
        )
      }

      is ActivateKeyboxState ->
        activateFullAccountDataStateMachine.model(
          props =
            ActivateFullAccountDataProps(
              keybox = state.keybox,
              onDeleteKeyboxAndExitOnboarding = {
                scope.launch {
                  appDataDeleter.deleteAll()
                  props.rollback()
                }
              }
            )
        )

      is OverwriteFullAccountCloudBackupWarningState -> {
        CreateFullAccountData.OverwriteFullAccountCloudBackupData(
          keybox = state.keybox,
          onOverwrite = state.onOverwrite,
          rollback = state.rollback
        )
      }

      is ReplaceWithLiteAccountRestoreState ->
        ReplaceWithLiteAccountRestoreData(
          keyboxToReplace = state.keybox,
          liteAccountCloudBackup = state.cloudBackupV2,
          onAccountUpgraded = { upgradedAccount ->
            dataState =
              OnboardKeyboxState(
                keybox = upgradedAccount.keybox,
                isSkipCloudBackupInstructions = true,
                ignoreExistingFullAccountCloudBackupFound = false
              )
          },
          onBack = {
            dataState =
              OnboardKeyboxState(
                keybox = state.keybox,
                isSkipCloudBackupInstructions = false,
                ignoreExistingFullAccountCloudBackupFound = false
              )
          }
        )
    }
  }

  @Composable
  private fun TransitionToActivateStepWhenOnboardCompleteEffect(
    transitionToActivateStep: () -> Unit,
  ) {
    // Listen to onboarding steps completion
    val cloudBackupStepState = rememberCloudBackupStepState()
    val notificationsStepState = rememberNotificationsStepState()

    LaunchedEffect(
      "transition-to-activate",
      cloudBackupStepState,
      notificationsStepState
    ) {
      val stepStates = mutableListOf(cloudBackupStepState, notificationsStepState)

      if (stepStates.all { it == OnboardingKeyboxStepState.Complete }) {
        // Transition to [ActivateKeyboxState] if all are complete
        transitionToActivateStep()
      }
    }
  }

  @Composable
  private fun rememberCloudBackupStepState(): OnboardingKeyboxStepState? {
    return remember {
      onboardingKeyboxStepStateDao.stateForStep(CloudBackup)
    }.collectAsState(null).value
  }

  @Composable
  private fun rememberNotificationsStepState(): OnboardingKeyboxStepState? {
    return remember {
      onboardingKeyboxStepStateDao.stateForStep(NotificationPreferences)
    }.collectAsState(null).value
  }
}

private sealed interface CreateAccountState {
  data object CreateKeyboxState : CreateAccountState

  data class OnboardKeyboxState(
    val keybox: Keybox,
    val isSkipCloudBackupInstructions: Boolean,
    val ignoreExistingFullAccountCloudBackupFound: Boolean,
  ) : CreateAccountState

  data class ReplaceWithLiteAccountRestoreState(
    val cloudBackupV2: CloudBackupV2,
    val keybox: Keybox,
  ) : CreateAccountState

  data class OverwriteFullAccountCloudBackupWarningState(
    val keybox: Keybox,
    val onOverwrite: () -> Unit,
    val rollback: () -> Unit,
  ) : CreateAccountState

  data class ActivateKeyboxState(
    val keybox: Keybox,
  ) : CreateAccountState
}
