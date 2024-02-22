package build.wallet.ui.model.list

import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.switch.SwitchModel

sealed interface ListItemAccessory {
  /** Shows a singular character with a grey circle background */
  data class CircularCharacterAccessory(
    val character: Char,
  ) : ListItemAccessory {
    val text = character.toString()
  }

  data class IconAccessory(
    /** The padding to apply to the icon on all sides  */
    val iconPadding: Int? = null,
    val model: IconModel,
  ) : ListItemAccessory {
    constructor(icon: Icon) :
      this(
        model =
          IconModel(
            icon,
            iconSize = IconSize.Small
          )
      )
  }

  data class SwitchAccessory(
    val model: SwitchModel,
  ) : ListItemAccessory

  data class ButtonAccessory(
    val model: ButtonModel,
  ) : ListItemAccessory

  data class TextAccessory(
    val text: String,
  ) : ListItemAccessory

  /**
   * Common accessories.
   */
  companion object {
    fun drillIcon(tint: IconTint? = null) =
      IconAccessory(
        iconPadding = null,
        IconModel(
          icon = Icon.SmallIconCaretRight,
          iconSize = IconSize.Small,
          iconBackgroundType = IconBackgroundType.Transient,
          iconTint = tint
        )
      )
  }
}
