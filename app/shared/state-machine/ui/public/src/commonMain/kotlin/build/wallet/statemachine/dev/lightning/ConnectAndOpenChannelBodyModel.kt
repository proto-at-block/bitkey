package build.wallet.statemachine.dev.lightning

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.ui.model.list.ListItemAccessory
import kotlinx.collections.immutable.ImmutableList

data class ConnectAndOpenChannelBodyModel(
  val channelsModel: ChannelsModel,
  val peerNodeId: String,
  val peerAddress: String,
  val fundingAmount: String,
  val onchainBalanceLabelString: String,
  val onPeerNodeIdChanged: (String) -> Unit,
  val onPeerAddressChanged: (String) -> Unit,
  val onFundingAmountChanged: (String) -> Unit,
  override val onBack: () -> Unit,
  val onConnectPressed: () -> Unit,
  // This is only used by the debug menu, it doesn't need a screen ID
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel()

data class ChannelsModel(
  val channelOpenRows: ImmutableList<ChannelOpenRowModel>,
)

data class ChannelOpenRowModel(
  val peerNodeId: String,
  val channelSubtitleText: String,
  val fundingTxId: String,
  val trailingAccessory: ListItemAccessory,
  val onClick: () -> Unit,
) {
  fun truncatedTxid(): String =
    fundingTxId.let {
      "${it.take(4)}...${it.takeLast(4)}"
    }
}
