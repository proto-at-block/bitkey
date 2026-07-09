package build.wallet.statemachine.moneyhome.card.gettingstarted

import build.wallet.statemachine.core.Icon.DotBitkey
import build.wallet.statemachine.core.Icon.Bitkey
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.DrillList
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

fun GettingStartedCardModel(
  animations: ImmutableList<CardModel.AnimationSet>?,
  taskModels: ImmutableList<GettingStartedTaskRowModel>,
  firmwareUpdateTile: CardModel.GettingStartedTileModel? = null,
) = CardModel(
  animation = animations,
  title =
    LabelModel.StringWithStyledSubstringModel.from(
      "Getting Started",
      emptyMap()
    ),
  content =
    (listOfNotNull(firmwareUpdateTile?.toLegacyListItemModel()) + taskModels.map { it.listItemModel })
      .takeIf { it.isNotEmpty() }
      ?.toImmutableList()
      ?.let {
        DrillList(items = it)
      },
  style = Outline(),
  kind =
    CardModel.Kind.GettingStarted(
      tiles = (listOfNotNull(firmwareUpdateTile) + taskModels.map { it.tileModel }).toImmutableList()
    )
)

fun FirmwareUpdateGettingStartedTileModel(onClick: () -> Unit) =
  CardModel.GettingStartedTileModel(
    id = CardModel.GettingStartedTileModel.Id.UpdateFirmware,
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

private fun CardModel.GettingStartedTileModel.toLegacyListItemModel(): ListItemModel =
  when (id) {
    CardModel.GettingStartedTileModel.Id.UpdateFirmware ->
      ListItemModel(
        title = title,
        leadingAccessory =
          ListItemAccessory.IconAccessory(
            model =
              IconModel(
                icon = Bitkey,
                iconSize = IconSize.Small
              )
          ),
        trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
        onClick = onClick
      )
    else -> error("Unsupported legacy list item conversion for getting started tile id: $id")
  }
