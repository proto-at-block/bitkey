package build.wallet.statemachine.moneyhome.card.gettingstarted

import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardContent.DrillList
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

fun GettingStartedCardModel(
  animations: ImmutableList<CardModel.AnimationSet>?,
  taskModels: ImmutableList<GettingStartedTaskRowModel>,
) = CardModel(
  animation = animations,
  title =
    LabelModel.StringWithStyledSubstringModel.from(
      "Getting Started",
      emptyMap()
    ),
  content =
    DrillList(
      items = taskModels.map { it.listItemModel }.toImmutableList()
    ),
  style = Outline
)
