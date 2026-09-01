package build.wallet.statemachine.recovery.hardware.fingerprintreset

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.moneyhome.card.CardModel

fun FingerprintResetCardModel(
  title: String,
  subtitle: String? = null,
  inverse: Boolean = false,
  onClick: () -> Unit,
) = CardModel.Status(
  id = "FingerprintReset",
  title = title,
  subtitle = subtitle,
  leadingImage =
    CardModel.Status.Image.StaticImage(
      icon = Icon.Fingerprint
    ),
  inverse = inverse,
  onClick = onClick
)
