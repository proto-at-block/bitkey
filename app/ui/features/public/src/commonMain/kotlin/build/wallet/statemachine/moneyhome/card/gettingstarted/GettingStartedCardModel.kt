package build.wallet.statemachine.moneyhome.card.gettingstarted

import build.wallet.statemachine.core.Icon.DotBitkey
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

fun GettingStartedCardModel(
  taskModels: ImmutableList<GettingStartedTaskRowModel>,
  firmwareUpdateTile: GettingStartedTileModel? = null,
): GettingStartedSectionModel {
  val allTiles = listOfNotNull(firmwareUpdateTile) + taskModels.map { it.tileModel }
  val (complete, incomplete) = allTiles.partition { it.isComplete }
  return GettingStartedSectionModel(
    title = "Getting Started",
    incomplete = incomplete.toImmutableList(),
    complete = complete.toImmutableList()
  )
}

fun FirmwareUpdateGettingStartedTileModel(onClick: () -> Unit) =
  GettingStartedTileModel(
    id = GettingStartedTileModel.Id.UpdateFirmware,
    title = "Update firmware",
    leadingIcon =
      IconModel(
        icon = DotBitkey,
        iconSize = IconSize.Regular
      ),
    isEnabled = true,
    isComplete = false,
    onClick = onClick
  )
