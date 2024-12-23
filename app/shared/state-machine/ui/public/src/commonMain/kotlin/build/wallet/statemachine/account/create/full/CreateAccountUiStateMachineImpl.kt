package build.wallet.statemachine.account.create.full

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.onboarding.CreateFullAccountService
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachineImpl.ActivateAccountState.AccountActivationError
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachineImpl.ActivateAccountState.ActivatingAccount
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiProps
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiStateMachine
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.account.CreateFullAccountData.*
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
    return when (val data = props.createFullAccountData) {
      is CreatingAccountData ->
        createKeyboxUiStateMachine.model(CreateKeyboxUiProps(data.context, data.rollback))

      is OnboardingAccountData -> {
        onboardFullAccountUiStateMachine.model(
          OnboardFullAccountUiProps(
            keybox = data.keybox,
            isSkipCloudBackupInstructions = data.isSkipCloudBackupInstructions,
            onFoundLiteAccountWithDifferentId = data.onFoundLiteAccountWithDifferentId,
            onOverwriteFullAccountCloudBackupWarning = data.onOverwriteFullAccountCloudBackupWarning,
            onOnboardingComplete = data.onOnboardingComplete
          )
        )
      }

      is ActivatingAccountData -> {
        var state: ActivateAccountState by remember { mutableStateOf(ActivatingAccount) }
        val scope = rememberStableCoroutineScope()

        when (val currentState = state) {
          is ActivatingAccount -> {
            LaunchedEffect("activate-account") {
              createFullAccountService.activateAccount(data.keybox)
                .onSuccess {
                  // noop - data state machine handle account activation
                }
                .onFailure {
                  state = AccountActivationError(it)
                }
            }

            LoadingSuccessBodyModel(
              message = "Loading your wallet...",
              state = LoadingSuccessBodyModel.State.Loading,
              id = GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX
            ).asRootScreen()
          }
          is AccountActivationError -> {
            NetworkErrorFormBodyModel(
              title = "We couldn’t create your wallet",
              isConnectivityError = currentState.error is NetworkError,
              onRetry = {
                state = ActivatingAccount
              },
              onBack = {
                scope.launch {
                  createFullAccountService.cancelAccountCreation()
                }
              },
              eventTrackerScreenId = CreateAccountEventTrackerScreenId.NEW_ACCOUNT_CREATION_FAILURE
            ).asRootScreen()
          }
        }
      }

      is ReplaceWithLiteAccountRestoreData ->
        replaceWithLiteAccountRestoreUiStateMachine
          .model(ReplaceWithLiteAccountRestoreUiProps(data))

      is OverwriteFullAccountCloudBackupData -> {
        overwriteFullAccountCloudBackupUiStateMachine.model(
          OverwriteFullAccountCloudBackupUiProps(data)
        )
      }
    }
  }

  private sealed interface ActivateAccountState {
    data object ActivatingAccount : ActivateAccountState

    data class AccountActivationError(
      val error: Throwable,
    ) : ActivateAccountState
  }
}
