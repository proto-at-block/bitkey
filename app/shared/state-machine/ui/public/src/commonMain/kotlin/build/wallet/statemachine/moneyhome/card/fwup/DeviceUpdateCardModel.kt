package build.wallet.statemachine.moneyhome.card.fwup

import build.wallet.statemachine.core.Icon.BitkeyDeviceRaised
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Gradient

fun DeviceUpdateCardModel(onUpdateDevice: () -> Unit) =
  CardModel(
    title =
      LabelModel.StringWithStyledSubstringModel.from(
        string = "Device update available",
        substringToColor = emptyMap()
      ),
    subtitle = null,
    leadingImage = CardModel.CardImage.StaticImage(BitkeyDeviceRaised),
    content = null,
    style = Gradient(),
    onClick = onUpdateDevice
  )
