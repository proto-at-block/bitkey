package build.wallet.statemachine.moneyhome.card.replacehardware

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel

fun ReplaceHardwareCardModel(onReplaceDevice: () -> Unit) =
  CardModel(
    title =
      LabelModel.StringWithStyledSubstringModel.from(
        string = "Replace your Bitkey device",
        substringToColor = emptyMap()
      ),
    subtitle = null,
    leadingImage = CardModel.CardImage.StaticImage(Icon.BitkeyDeviceRaised),
    content = null,
    style = CardModel.CardStyle.Gradient(),
    onClick = onReplaceDevice
  )
