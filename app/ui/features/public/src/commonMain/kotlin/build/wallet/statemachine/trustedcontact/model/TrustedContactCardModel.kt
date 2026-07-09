package build.wallet.statemachine.trustedcontact.model

import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.bitkey.relationships.TrustedContactRole.Companion.Beneficiary
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Gradient.BackgroundColor
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact

fun TrustedContactCardModel(
  contact: TrustedContact,
  buttonText: String,
  onClick: () -> Unit,
  buttonTreatment: ButtonModel.Treatment = ButtonModel.Treatment.Primary,
  backgroundColor: BackgroundColor = BackgroundColor.Default,
  subtitleText: String = when {
    Beneficiary == contact.roles.singleOrNull() -> "Beneficiary"
    else -> "$buttonText Recovery Contact"
  },
) = CardModel(
  leadingImage = CardModel.CardImage.StaticImage(Icon.ShieldPerson),
  title =
    LabelModel.StringWithStyledSubstringModel.from(
      string = contact.trustedContactAlias.alias,
      substringToColor = emptyMap()
    ),
  subtitle = subtitleText,
  trailingButton =
    ButtonModel(
      text = buttonText,
      size = Compact,
      onClick = StandardClick(onClick),
      treatment = buttonTreatment
    ),
  onClick = onClick,
  content = null,
  style = CardModel.CardStyle.Gradient(
    backgroundColor = backgroundColor
  )
)
