package build.wallet.statemachine.moneyhome.card.replacehardware

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.moneyhome.card.CardModel

fun SetupHardwareCardModel(
  setupType: HardwareSetupType,
  onReplaceDevice: () -> Unit,
) = CardModel(
  title =
    LabelModel.StringWithStyledSubstringModel.from(
      string = when (setupType) {
        HardwareSetupType.Replace -> "Replace your Bitkey device"
        HardwareSetupType.PairNew -> "Pair a Bitkey device"
      },
      substringToColor = emptyMap()
    ),
  subtitle = null,
  leadingImage = CardModel.CardImage.StaticImage(Icon.BitkeyDeviceRaised),
  content = null,
  style = CardModel.CardStyle.Gradient(),
  onClick = onReplaceDevice
)

/**
 * Distinguishes whether the user is replacing hardware as part of a
 * recovery step or needs to pair a new device after being wiped.
 */
enum class HardwareSetupType {
  Replace,
  PairNew,
}
