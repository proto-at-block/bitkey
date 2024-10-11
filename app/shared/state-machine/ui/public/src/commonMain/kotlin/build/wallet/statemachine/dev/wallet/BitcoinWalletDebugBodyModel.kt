package build.wallet.statemachine.dev.wallet

import build.wallet.compose.collections.buildImmutableList
import build.wallet.statemachine.core.Icon.SmallIconCheck
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupModel.HeaderTreatment.PRIMARY
import build.wallet.ui.model.list.ListGroupStyle.DIVIDER
import build.wallet.ui.model.list.ListItemAccessory.IconAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemSideTextTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList

internal data class BitcoinWalletDebugBodyModel(
  override val onBack: () -> Unit,
  val utxos: List<UtxoRowModel>?,
) : FormBodyModel(
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
                leadingAccessory = run {
                  val iconModel = when {
                    it.isConfirmed -> TnxConfirmedIcon
                    else -> TnxPendingIcon
                  }
                  IconAccessory(model = iconModel)
                },
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
 * @param isConfirmed whether the transaction [txId] associated with the UTXO is confirmed.
 */
internal data class UtxoRowModel(
  val value: String,
  val txId: String,
  val isConfirmed: Boolean,
  val onClick: () -> Unit,
)

private val TnxConfirmedIcon = IconModel(icon = SmallIconCheck, iconSize = IconSize.Small)
private val TnxPendingIcon = IconModel(iconImage = IconImage.Loader, iconSize = IconSize.Small)
