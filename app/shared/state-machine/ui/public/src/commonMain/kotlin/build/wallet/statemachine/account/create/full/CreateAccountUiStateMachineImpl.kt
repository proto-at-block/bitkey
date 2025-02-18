package build.wallet.statemachine.account.create.full

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.onboarding.CreateFullAccountService
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachineImpl.ActivateAccountState.AccountActivationError
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiProps
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class CreateAccountUiStateMachineImpl(
  private val createFullAccountService: CreateFullAccountService,
  private val createKeyboxUiStateMachine: CreateKeyboxUiStateMachine,
  private val onboardFullAccountUiStateMachine: OnboardFullAccountUiStateMachine,
  private val replaceWithLiteAccountRestoreUiStateMachine:
    ReplaceWithLiteAccountRestoreUiStateMachine,
  private val overwriteFullAccountCloudBackupUiStateMachine:
    OverwriteFullAccountCloudBackupUiStateMachine,
) : CreateAccountUiStateMachine {
  @Composable
  override fun model(props: CreateAccountUiProps): ScreenModel {
    val scope = rememberStableCoroutineScope()

    var uiState: ActivateAccountState by remember {
      when (props.fullAccount) {
        null -> mutableStateOf(ActivateAccountState.CreateKeyboxState)
        else -> mutableStateOf(
          ActivateAccountState.OnboardKeyboxState(
            account = props.fullAccount,
            isSkipCloudBackupInstructions = false
          )
        )
      }
    }

    return when (val state = uiState) {
      ActivateAccountState.CreateKeyboxState -> createKeyboxUiStateMachine.model(
        CreateKeyboxUiProps(
          props.context,
          props.rollback,
          onAccountCreated = {
            uiState = ActivateAccountState.OnboardKeyboxState(
              account = it,
              isSkipCloudBackupInstructions = false
            )
          }
        )
      )
      is ActivateAccountState.OnboardKeyboxState -> onboardFullAccountUiStateMachine.model(
        OnboardFullAccountUiProps(
          fullAccount = state.account,
          isSkipCloudBackupInstructions = state.isSkipCloudBackupInstructions,
          onFoundLiteAccountWithDifferentId = { cloudBackup ->
            // Found a Lite Account with a different account ID. Upgrade the Lite Account
            // instead to replace this full account in order to preserve the protected
            // customers.
            uiState = ActivateAccountState.ReplaceWithLiteAccountRestoreState(
              cloudBackupV2 = cloudBackup,
              account = state.account
            )
          },
          onOverwriteFullAccountCloudBackupWarning = {
            uiState = ActivateAccountState.OverwriteFullAccountCloudBackupWarningState(
              account = state.account,
              rollback = props.rollback
            )
          },
          onOnboardingComplete = {
            uiState = ActivateAccountState.ActivateKeyboxState(account = state.account)
          }
        )
      )
      is AccountActivationError -> NetworkErrorFormBodyModel(
        title = "We couldn’t create your wallet",
        isConnectivityError = state.error is NetworkError,
        onRetry = {
          uiState = ActivateAccountState.ActivateKeyboxState(state.account)
        },
        onBack = {
          scope.launch {
            createFullAccountService.cancelAccountCreation()
          }
        },
        eventTrackerScreenId = CreateAccountEventTrackerScreenId.NEW_ACCOUNT_CREATION_FAILURE
      ).asRootScreen()

      is ActivateAccountState.OverwriteFullAccountCloudBackupWarningState -> overwriteFullAccountCloudBackupUiStateMachine.model(
        OverwriteFullAccountCloudBackupUiProps(
          keybox = state.account.keybox,
          onOverwrite = {
            uiState = ActivateAccountState.OnboardKeyboxState(
              account = state.account,
              isSkipCloudBackupInstructions = true
            )
          },
          rollback = state.rollback
        )
      )
      is ActivateAccountState.ReplaceWithLiteAccountRestoreState ->
        replaceWithLiteAccountRestoreUiStateMachine
          .model(
            ReplaceWithLiteAccountRestoreUiProps(
              keyboxToReplace = state.account.keybox,
              liteAccountCloudBackup = state.cloudBackupV2,
              onAccountUpgraded = { upgradedAccount ->
                uiState = ActivateAccountState.OnboardKeyboxState(
                  account = upgradedAccount,
                  isSkipCloudBackupInstructions = true
                )
              },
              onBack = {
                uiState = ActivateAccountState.OnboardKeyboxState(
                  account = state.account,
                  isSkipCloudBackupInstructions = false
                )
              }
            )
          )
      is ActivateAccountState.ActivateKeyboxState -> {
        LaunchedEffect("activate-account") {
          createFullAccountService.activateAccount(state.account.keybox)
            .onSuccess {
              props.onOnboardingComplete(state.account)
            }
            .onFailure {
              uiState = AccountActivationError(it, state.account)
            }
        }

        LoadingSuccessBodyModel(
          message = "Loading your wallet...",
          state = LoadingSuccessBodyModel.State.Loading,
          id = GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX
        ).asRootScreen()
      }
    }
  }

  private sealed interface ActivateAccountState {
    data class AccountActivationError(
      val error: Throwable,
      val account: FullAccount,
    ) : ActivateAccountState

    data object CreateKeyboxState : ActivateAccountState

    data class OnboardKeyboxState(
      val account: FullAccount,
      val isSkipCloudBackupInstructions: Boolean,
    ) : ActivateAccountState

    data class ReplaceWithLiteAccountRestoreState(
      val cloudBackupV2: CloudBackupV2,
      val account: FullAccount,
    ) : ActivateAccountState

    data class OverwriteFullAccountCloudBackupWarningState(
      val account: FullAccount,
      val rollback: () -> Unit,
    ) : ActivateAccountState

    data class ActivateKeyboxState(
      val account: FullAccount,
    ) : ActivateAccountState
  }
}
