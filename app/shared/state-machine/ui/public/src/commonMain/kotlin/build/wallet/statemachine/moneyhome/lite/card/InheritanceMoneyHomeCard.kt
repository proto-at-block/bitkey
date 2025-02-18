package build.wallet.statemachine.moneyhome.lite.card

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.button.ButtonModel

fun InheritanceMoneyHomeCard(
  onIHaveABitkey: () -> Unit,
  onGetABitkey: () -> Unit,
) = CardModel(
  heroImage = Icon.InheritancePlanHero,
  title =
    LabelModel.StringWithStyledSubstringModel.from(
      "Use your bitkey to finish setting up your inheritance plan",
      emptyMap()
    ),
  subtitle = "To accept an inheritance plan, you’ll need your own Bitkey.",
  content = null,
  style = CardModel.CardStyle.Outline,
  primaryButton = ButtonModel(
    text = "Accept invite",
    requiresBitkeyInteraction = false,
    treatment = ButtonModel.Treatment.Primary,
    size = ButtonModel.Size.Footer,
    onClick = onIHaveABitkey
  ),
  secondaryButton = ButtonModel(
    text = "Get a Bitkey",
    requiresBitkeyInteraction = false,
    treatment = ButtonModel.Treatment.Secondary,
    size = ButtonModel.Size.Footer,
    leadingIcon = Icon.SmallIconArrowUpRight,
    onClick = onGetABitkey
  )
)
