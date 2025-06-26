package build.wallet.statemachine.recovery.hardware.fingerprintreset

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel

fun FingerprintResetCardModel(
  title: String,
  subtitle: String? = null,
  onClick: () -> Unit,
) = CardModel(
  title = LabelModel.StringWithStyledSubstringModel.from(
    string = title,
    substringToColor = emptyMap()
  ),
  subtitle = subtitle,
  leadingImage =
    CardModel.CardImage.StaticImage(
      icon = Icon.SmallIconFingerprint
    ),
  content = null,
  style = CardModel.CardStyle.Gradient(
    backgroundColor = CardModel.CardStyle.Gradient.BackgroundColor.Default
  ),
  onClick = onClick
)
