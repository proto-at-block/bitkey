package build.wallet.statemachine.moneyhome.lite.card

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel

fun BuyOwnBitkeyMoneyHomeCardModel(onClick: () -> Unit) =
  CardModel.Hero(
    heroImage = Icon.BuyOwnBitkeyHero,
    title =
      LabelModel.StringWithStyledSubstringModel.from(
        "Buy your own Bitkey",
        emptyMap()
      ),
    subtitle = "The safe, easy way to own and manage your bitcoin.",
    onClick = onClick
  )
