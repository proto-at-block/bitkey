package build.wallet.statemachine.dev.lightning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.ldk.LdkNodeService
import build.wallet.ldk.bindings.ChannelDetails
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.Clipboard
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.dev.lightning.ConnectAndOpenChannelStateMachine.Props
import build.wallet.statemachine.dev.lightning.ConnectAndOpenChannelStateMachineImpl.UiState.ClosingChannelUiState
import build.wallet.statemachine.dev.lightning.ConnectAndOpenChannelStateMachineImpl.UiState.OpeningChannelUiState
import build.wallet.statemachine.dev.lightning.ConnectAndOpenChannelStateMachineImpl.UiState.RefreshingUiState
import build.wallet.statemachine.dev.lightning.ConnectAndOpenChannelStateMachineImpl.UiState.WaitingUiState
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Small
import build.wallet.ui.model.list.ListItemAccessory
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.collections.immutable.toImmutableList

class ConnectAndOpenChannelStateMachineImpl(
  val ldkNodeService: LdkNodeService,
  val clipboard: Clipboard,
) : ConnectAndOpenChannelStateMachine {
  @Composable
  override fun model(props: Props): BodyModel {
    var uiState: UiState by remember { mutableStateOf(RefreshingUiState) }
    var nodeIdString by remember { mutableStateOf("") }
    var peerAddressString by remember { mutableStateOf("") }

    var onchainBalance by remember { mutableStateOf("") }

    var fundingAmount by remember { mutableStateOf("") }
    val secondaryAmount by remember(fundingAmount) {
      derivedStateOf {
        fundingAmount.toBigInteger()
      }
    }

    var channels: List<ChannelDetails> by remember { mutableStateOf(emptyList()) }
    val channelsModel by remember(channels) {
      derivedStateOf {
        ChannelsModel(
          channelOpenRows =
            channels.map {
              ChannelOpenRowModel(
                peerNodeId = it.counterparty,
                channelSubtitleText = getChannelRowSubtitleText(it),
                fundingTxId = it.fundingTxo?.txid ?: "",
                trailingAccessory =
                  getTrailingAccessoryForChannel(
                    isReady = it.isChannelReady,
                    handleDeleteChannel = {
                      uiState = ClosingChannelUiState(it)
                    }
                  ),
                onClick = {
                  clipboard.setItem(item = PlainText(it.fundingTxo?.txid ?: ""))
                }
              )
            }.toImmutableList()
        )
      }
    }

    when (val s = uiState) {
      is RefreshingUiState -> {
        LaunchedEffect("listing-channels") {
          channels = ldkNodeService.listChannels()
          uiState = WaitingUiState
        }

        LaunchedEffect("loading-balance") {
          ldkNodeService.spendableOnchainBalance()
            .onSuccess {
              onchainBalance = it.toString()
            }
        }
        uiState = WaitingUiState
      }

      is ClosingChannelUiState -> {
        LaunchedEffect("close-channel") {
          log { "Closing channel ${s.channel.channelId} with counterparty ${s.channel.counterparty}" }
          ldkNodeService
            .closeChannel(s.channel)
            .onSuccess {
              log { "Channel closed!" }
            }
            .result
            .logFailure { "Error closing Lightning channel" }
        }
      }

      is OpeningChannelUiState -> {
        LaunchedEffect("connect-and-open-channel") {
          log { "Opening channel with ${s.nodeConnectionString} for ${s.fundingAmount} sats!" }
          ldkNodeService
            .connectAndOpenChannel(
              nodePublicKey = nodeIdString,
              address = peerAddressString,
              channelAmountSats = secondaryAmount
            )
            .onSuccess {
              log { "Channel open accepted! Funding transaction processing..." }
            }
            .result
            .logFailure {
              "LDK: Error opening channel"
            }

          uiState = RefreshingUiState
        }
      }

      else -> Unit
    }

    return ConnectAndOpenChannelBodyModel(
      channelsModel = channelsModel,
      peerNodeId = nodeIdString,
      peerAddress = peerAddressString,
      fundingAmount = fundingAmount,
      onBack = props.onBack,
      onchainBalanceLabelString = "You have $onchainBalance sats",
      onPeerNodeIdChanged = {
        nodeIdString = it
      },
      onPeerAddressChanged = {
        peerAddressString = it
      },
      onFundingAmountChanged = {
        fundingAmount = it
      },
      onConnectPressed = {
        uiState = OpeningChannelUiState(nodeIdString, fundingAmount)
      }
    )
  }

  private fun getChannelRowSubtitleText(channel: ChannelDetails): String {
    return if (channel.isChannelReady) {
      "${channel.outboundCapacityMsat}/${channel.inboundCapacityMsat} msats"
    } else {
      "${channel.confirmations}/${channel.confirmationsRequired} confirmations"
    }
  }

  fun getTrailingAccessoryForChannel(
    isReady: Boolean,
    handleDeleteChannel: () -> Unit,
  ): ListItemAccessory {
    return if (!isReady) {
      ListItemAccessory.IconAccessory(
        model =
          IconModel(
            icon = Icon.SmallIconWarning,
            iconSize = Small
          )
      )
    } else {
      ListItemAccessory.ButtonAccessory(
        model =
          ButtonModel(
            text = "Close Channel",
            treatment = TertiaryDestructive,
            size = Compact,
            onClick = StandardClick(handleDeleteChannel)
          )
      )
    }
  }

  private sealed class UiState {
    data object WaitingUiState : UiState()

    data class OpeningChannelUiState(
      val nodeConnectionString: String,
      val fundingAmount: String,
    ) : UiState()

    data class ClosingChannelUiState(
      val channel: ChannelDetails,
    ) : UiState()

    data object RefreshingUiState : UiState()
  }
}
