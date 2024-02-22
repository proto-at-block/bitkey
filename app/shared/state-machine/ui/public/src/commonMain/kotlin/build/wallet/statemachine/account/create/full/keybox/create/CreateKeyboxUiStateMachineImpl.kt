package build.wallet.statemachine.account.create.full.keybox.create

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.PairHardwareEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareProps
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareUiStateMachine
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.core.ScreenPresentationStyle.RootFullScreen
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData.CreateKeyboxErrorData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData.CreatingAppKeysData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData.HasAppAndHardwareKeysData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData.HasAppKeysData
import build.wallet.statemachine.data.account.CreateFullAccountData.CreateKeyboxData.PairingWithServerData

class CreateKeyboxUiStateMachineImpl(
  private val pairNewHardwareUiStateMachine: PairNewHardwareUiStateMachine,
) : CreateKeyboxUiStateMachine {
  @Composable
  override fun model(props: CreateKeyboxUiProps): ScreenModel {
    return when (val dataState: CreateKeyboxData = props.createKeyboxData) {
      is CreatingAppKeysData ->
        CreatingAppKeysUiModel(dataState)

      is HasAppKeysData ->
        HasAppKeysUiModel(dataState)

      is HasAppAndHardwareKeysData, is PairingWithServerData ->
        PairingWithServerModel()

      is CreateKeyboxErrorData ->
        ErrorFormBodyModel(
          onBack = dataState.onBack,
          title = dataState.title,
          subline = dataState.subline,
          primaryButton =
            dataState.primaryButton.let {
              ButtonDataModel(text = it.text, onClick = it.onClick)
            },
          secondaryButton =
            dataState.secondaryButton?.let {
              ButtonDataModel(text = it.text, onClick = it.onClick)
            },
          eventTrackerScreenId = dataState.eventTrackerScreenId
        ).asRootScreen()
    }
  }

  @Composable
  private fun CreatingAppKeysUiModel(dataState: CreatingAppKeysData): ScreenModel {
    // CreatingAppKeysData is a loading state, so don't handle onSuccess. We should transition to
    // [HasAppKeysData] before we need to handle it, we'll log an error if that assumption is wrong.
    return pairNewHardwareUiStateMachine.model(
      props =
        PairNewHardwareProps(
          keyboxConfig = dataState.keyboxConfig,
          screenPresentationStyle = RootFullScreen,
          onExit = dataState.rollback,
          onSuccess = null,
          eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
        )
    )
  }

  @Composable
  private fun HasAppKeysUiModel(dataState: HasAppKeysData): ScreenModel {
    return pairNewHardwareUiStateMachine.model(
      props =
        PairNewHardwareProps(
          keyboxConfig = dataState.keyboxConfig,
          screenPresentationStyle = Root,
          onExit = dataState.rollback,
          onSuccess = { dataState.onPairHardwareComplete(it) },
          eventTrackerContext = PairHardwareEventTrackerScreenIdContext.ACCOUNT_CREATION
        )
    )
  }

  @Composable
  private fun PairingWithServerModel() =
    LoadingBodyModel(
      message = "Creating account...",
      id = CreateAccountEventTrackerScreenId.NEW_ACCOUNT_SERVER_KEYS_LOADING
    ).asRootScreen()
}
