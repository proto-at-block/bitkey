package build.wallet.statemachine.trustedcontact.model

import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.bitkey.relationships.TrustedContactRole.Companion.Beneficiary
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.moneyhome.card.CardModel

fun TrustedContactCardModel(
  contact: TrustedContact,
  buttonText: String,
  onClick: () -> Unit,
  inverse: Boolean = false,
  subtitleText: String = when {
    Beneficiary == contact.roles.singleOrNull() -> "Beneficiary"
    else -> "$buttonText Recovery Contact"
  },
) = CardModel.Status(
  id = "TrustedContact:${contact.id.value}",
  leadingImage = CardModel.Status.Image.StaticImage(Icon.ShieldPerson),
  title = contact.trustedContactAlias.alias,
  subtitle = subtitleText,
  onClick = onClick,
  inverse = inverse
)
