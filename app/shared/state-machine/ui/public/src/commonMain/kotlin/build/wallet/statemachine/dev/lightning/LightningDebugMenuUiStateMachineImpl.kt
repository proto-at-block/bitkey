package build.wallet.statemachine.dev.lightning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.ldk.LdkNodeService
import build.wallet.logging.log
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.Clipboard
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.dev.lightning.LightningDebugMenuUiStateMachine.LightningDebugMenuUiProps
import build.wallet.statemachine.dev.lightning.UiState.ConnectingAndOpeningChannelUiState
import build.wallet.statemachine.dev.lightning.UiState.LoadingFundingAddressUiState
import build.wallet.statemachine.dev.lightning.UiState.SendAndReceiveUiState
import build.wallet.statemachine.dev.lightning.UiState.ShowingLightningDebugOptionsUiState
import build.wallet.statemachine.dev.lightning.UiState.SyncingWalletsUiState

class LightningDebugMenuUiStateMachineImpl(
  val clipboard: Clipboard,
  val connectAndOpenChannelStateMachine: ConnectAndOpenChannelStateMachine,
  val lightningSendReceiveUiStateMachine: LightningSendReceiveUiStateMachine,
  val ldkNodeService: LdkNodeService,
) : LightningDebugMenuUiStateMachine {
  @Composable
  override fun model(props: LightningDebugMenuUiProps): ScreenModel {
    var uiState: UiState by remember { mutableStateOf(ShowingLightningDebugOptionsUiState(null)) }

    val lightningNodeId = ldkNodeService.nodeId().get().orEmpty()

    when (uiState) {
      is LoadingFundingAddressUiState -> {
        LaunchedEffect("load-funding-address") {
          ldkNodeService.getFundingAddress()
            .onSuccess {
              uiState = ShowingLightningDebugOptionsUiState(fundingAddress = it)
            }
            .onFailure {
              uiState = ShowingLightningDebugOptionsUiState(fundingAddress = null)
            }
        }
      }

      is SyncingWalletsUiState -> {
        LaunchedEffect("sync-wallets") {
          ldkNodeService.syncWallets()
            .onFailure {
              log { "LDK:: Unable to sync on-chain wallet: ${it.message}" }
            }
          uiState = ShowingLightningDebugOptionsUiState(fundingAddress = null)
        }
      }

      else -> Unit
    }

    return when (val s = uiState) {
      is ConnectingAndOpeningChannelUiState -> {
        connectAndOpenChannelStateMachine.model(
          ConnectAndOpenChannelStateMachine.Props(
            onBack = {
              uiState = ShowingLightningDebugOptionsUiState(fundingAddress = null)
            }
          )
        ).asModalScreen()
      }

      is SendAndReceiveUiState -> {
        lightningSendReceiveUiStateMachine.model(
          LightningSendReceiveUiProps(
            onBack = {
              uiState = ShowingLightningDebugOptionsUiState(fundingAddress = null)
            }
          )
        ).asModalScreen()
      }

      is ShowingLightningDebugOptionsUiState -> {
        val onchainBalance = ldkNodeService.spendableOnchainBalance().toString()

        LightningDebugBodyModel(
          nodeId = lightningNodeId,
          spendableOnchainBalance = "$onchainBalance sats",
          onBack = props.onBack,
          fundingAlertModel =
            when (s.fundingAddress) {
              null -> null
              else -> {
                fundingAddressAlertModel(
                  subline = s.fundingAddress,
                  onPrimaryButtonClick = { clipboard.setItem(item = PlainText(data = s.fundingAddress)) },
                  onDismiss = { uiState = s.copy(fundingAddress = null) }
                )
              }
            },
          onGetFundingAddressClicked = {
            uiState = LoadingFundingAddressUiState
          },
          onSyncWalletClicked = {
            uiState = SyncingWalletsUiState
          },
          onConnectAndOpenChannelButtonClicked = {
            uiState = ConnectingAndOpeningChannelUiState
          },
          onSendAndReceivePaymentClicked = {
            uiState = SendAndReceiveUiState
          }
        ).asModalScreen()
      }

      else ->
        LightningDebugBodyModel(
          nodeId = lightningNodeId,
          spendableOnchainBalance = "",
          onBack = props.onBack,
          fundingAlertModel = null,
          onGetFundingAddressClicked = {},
          onSyncWalletClicked = {},
          onConnectAndOpenChannelButtonClicked = {},
          onSendAndReceivePaymentClicked = {}
        ).asModalScreen()
    }
  }
}

private sealed interface UiState {
  data object LoadingFundingAddressUiState : UiState

  data object SyncingWalletsUiState : UiState

  data object ConnectingAndOpeningChannelUiState : UiState

  data object SendAndReceiveUiState : UiState

  data class ShowingLightningDebugOptionsUiState(
    val fundingAddress: String?,
  ) : UiState
}
