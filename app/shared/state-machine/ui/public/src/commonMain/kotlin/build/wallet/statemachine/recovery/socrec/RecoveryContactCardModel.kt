package build.wallet.statemachine.recovery.socrec

import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact

fun RecoveryContactCardModel(
  contact: TrustedContact,
  buttonText: String,
  onClick: () -> Unit,
  buttonTreatment: ButtonModel.Treatment = ButtonModel.Treatment.Primary,
) = CardModel(
  leadingImage = CardModel.CardImage.StaticImage(Icon.MediumIconTrustedContact),
  title =
    LabelModel.StringWithStyledSubstringModel.from(
      string = contact.trustedContactAlias.alias,
      substringToColor = emptyMap()
    ),
  subtitle = "Trusted Contact",
  trailingButton =
    ButtonModel(
      text = buttonText,
      size = Compact,
      onClick = StandardClick(onClick),
      treatment = buttonTreatment
    ),
  onClick = onClick,
  content = null,
  style = CardModel.CardStyle.Gradient()
)
