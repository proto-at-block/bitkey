package build.wallet.statemachine.account.create.full

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiProps
import build.wallet.statemachine.account.create.full.keybox.create.CreateKeyboxUiStateMachine
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.account.CreateFullAccountData.ActivateKeyboxDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.ActivateKeyboxDataFull.ActivatingKeyboxDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.ActivateKeyboxDataFull.FailedToActivateKeyboxDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData
import build.wallet.statemachine.data.account.CreateFullAccountData.OnboardKeyboxDataFull
import build.wallet.statemachine.data.account.CreateFullAccountData.OverwriteFullAccountCloudBackupData
import build.wallet.statemachine.data.account.CreateFullAccountData.ReplaceWithLiteAccountRestoreData

class CreateAccountUiStateMachineImpl(
  private val createKeyboxUiStateMachine: CreateKeyboxUiStateMachine,
  private val onboardKeyboxUiStateMachine: OnboardKeyboxUiStateMachine,
  private val replaceWithLiteAccountRestoreUiStateMachine:
    ReplaceWithLiteAccountRestoreUiStateMachine,
  private val overwriteFullAccountCloudBackupUiStateMachine:
    OverwriteFullAccountCloudBackupUiStateMachine,
) : CreateAccountUiStateMachine {
  @Composable
  override fun model(props: CreateAccountUiProps): ScreenModel {
    return when (val data = props.createFullAccountData) {
      is CreateKeyboxData ->
        createKeyboxUiStateMachine.model(
          CreateKeyboxUiProps(
            createKeyboxData = data,
            isHardwareFake = props.isHardwareFake
          )
        )

      is OnboardKeyboxDataFull ->
        onboardKeyboxUiStateMachine.model(OnboardKeyboxUiProps(data))

      is ActivateKeyboxDataFull ->
        ActivateKeyboxScreen(data = data)

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

  @Composable
  private fun ActivateKeyboxScreen(data: ActivateKeyboxDataFull): ScreenModel {
    return when (data) {
      is ActivatingKeyboxDataFull ->
        LoadingBodyModel(
          message = "Loading your wallet...",
          id = GeneralEventTrackerScreenId.LOADING_SAVING_KEYBOX
        ).asRootScreen()

      is FailedToActivateKeyboxDataFull ->
        NetworkErrorFormBodyModel(
          title = "We couldn’t create your wallet",
          isConnectivityError = data.isConnectivityError,
          onRetry = data.retry,
          onBack = data.onDeleteKeyboxAndExitOnboarding,
          eventTrackerScreenId = CreateAccountEventTrackerScreenId.NEW_ACCOUNT_CREATION_FAILURE
        ).asRootScreen()
    }
  }
}
