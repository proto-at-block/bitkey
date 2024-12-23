package build.wallet.statemachine.data.account.create

import androidx.compose.runtime.*
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreatingAccountData
import build.wallet.statemachine.data.account.CreateFullAccountData.ReplaceWithLiteAccountRestoreData
import build.wallet.statemachine.data.account.create.CreateAccountState.*

@BitkeyInject(AppScope::class)
class CreateFullAccountDataStateMachineImpl : CreateFullAccountDataStateMachine {
  @Composable
  override fun model(props: CreateFullAccountDataProps): CreateFullAccountData {
    // Set the initial state based on persisted onboarding keybox.
    var dataState: CreateAccountState by remember {
      mutableStateOf(
        when (props.onboardingKeybox) {
          null -> CreateKeyboxState
          else ->
            OnboardKeyboxState(
              keybox = props.onboardingKeybox,
              isSkipCloudBackupInstructions = false
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
                isSkipCloudBackupInstructions = false
              )
          }
        }
        CreatingAccountData(
          context = props.context,
          rollback = props.rollback
        )
      }

      is OnboardKeyboxState -> {
        CreateFullAccountData.OnboardingAccountData(
          keybox = state.keybox,
          isSkipCloudBackupInstructions = state.isSkipCloudBackupInstructions,
          onFoundLiteAccountWithDifferentId = { cloudBackup ->
            // Found a Lite Account with a different account ID. Upgrade the Lite Account
            // instead to replace this full account in order to preserve the protected
            // customers.
            dataState = ReplaceWithLiteAccountRestoreState(
              cloudBackupV2 = cloudBackup,
              keybox = state.keybox
            )
          },
          onOverwriteFullAccountCloudBackupWarning = {
            dataState = OverwriteFullAccountCloudBackupWarningState(
              keybox = state.keybox,
              rollback = props.rollback
            )
          },
          onOnboardingComplete = {
            dataState = ActivateKeyboxState(keybox = state.keybox)
          }
        )
      }

      is ActivateKeyboxState -> CreateFullAccountData.ActivatingAccountData(state.keybox)

      is OverwriteFullAccountCloudBackupWarningState -> {
        CreateFullAccountData.OverwriteFullAccountCloudBackupData(
          keybox = state.keybox,
          onOverwrite = {
            dataState = OnboardKeyboxState(
              keybox = state.keybox,
              isSkipCloudBackupInstructions = true
            )
          },
          rollback = state.rollback
        )
      }

      is ReplaceWithLiteAccountRestoreState ->
        ReplaceWithLiteAccountRestoreData(
          keyboxToReplace = state.keybox,
          liteAccountCloudBackup = state.cloudBackupV2,
          onAccountUpgraded = { upgradedAccount ->
            dataState = OnboardKeyboxState(
              keybox = upgradedAccount.keybox,
              isSkipCloudBackupInstructions = true
            )
          },
          onBack = {
            dataState = OnboardKeyboxState(
              keybox = state.keybox,
              isSkipCloudBackupInstructions = false
            )
          }
        )
    }
  }
}

private sealed interface CreateAccountState {
  data object CreateKeyboxState : CreateAccountState

  data class OnboardKeyboxState(
    val keybox: Keybox,
    val isSkipCloudBackupInstructions: Boolean,
  ) : CreateAccountState

  data class ReplaceWithLiteAccountRestoreState(
    val cloudBackupV2: CloudBackupV2,
    val keybox: Keybox,
  ) : CreateAccountState

  data class OverwriteFullAccountCloudBackupWarningState(
    val keybox: Keybox,
    val rollback: () -> Unit,
  ) : CreateAccountState

  data class ActivateKeyboxState(
    val keybox: Keybox,
  ) : CreateAccountState
}
