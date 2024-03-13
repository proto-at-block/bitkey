package build.wallet.statemachine.moneyhome.card.backup

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardImage.StaticImage
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Gradient
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Treatment.Warning

// TODO: polish UI
fun CloudBackupHealthCardModel(
  title: String,
  onActionClick: () -> Unit,
) = CardModel(
  leadingImage = StaticImage(Icon.SmallIconWarning),
  title = LabelModel.StringWithStyledSubstringModel.from(
    string = title,
    substringToColor = emptyMap()
  ),
  trailingButton = ButtonModel(
    text = "->",
    size = ButtonModel.Size.Compact,
    treatment = Warning,
    onClick = StandardClick(onActionClick)
  ),
  content = null,
  style = Gradient
)
