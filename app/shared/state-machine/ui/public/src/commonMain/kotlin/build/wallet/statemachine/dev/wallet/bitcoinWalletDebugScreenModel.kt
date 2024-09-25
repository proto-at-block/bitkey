package build.wallet.statemachine.dev.wallet

import build.wallet.compose.collections.buildImmutableList
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupModel.HeaderTreatment.PRIMARY
import build.wallet.ui.model.list.ListGroupStyle.DIVIDER
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList

internal fun bitcoinWalletDebugUiStateMachine(
  onBack: () -> Unit,
  utxos: List<UtxoRowModel>?,
) = FormBodyModel(
  id = null,
  onBack = onBack,
  toolbar = ToolbarModel(
    leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onBack),
    middleAccessory = ToolbarMiddleAccessoryModel(title = "Bitcoin Wallet")
  ),
  header = null,
  mainContentList = buildImmutableList {
    if (utxos != null) {
      ListGroup(
        ListGroupModel(
          header = "Unspent Transaction Outputs (UTXOs)",
          items = utxos.map {
            ListItemModel(
              title = it.value,
              sideText = it.txId,
              onClick = it.onClick,
              sideTextTint = ListItemSideTextTint.SECONDARY
            )
          }.toImmutableList(),
          style = DIVIDER,
          headerTreatment = PRIMARY
        )
      ).also(::add)
    }
  },
  primaryButton = null
)

/**
 * Represents a single UTXO.
 *
 * @param value string representation of the UTXO value (sats/btc).
 * @param txId transaction ID of the UTXO.
 */
internal data class UtxoRowModel(
  val value: String,
  val txId: String,
  val onClick: () -> Unit,
)
