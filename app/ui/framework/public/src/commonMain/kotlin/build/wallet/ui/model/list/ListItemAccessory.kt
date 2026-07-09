package build.wallet.ui.model.list

import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.tokens.LabelType

sealed interface ListItemAccessory {
  /** Shows a singular character with a grey circle background */
  data class CircularCharacterAccessory(
    val character: Char,
    val circleSize: IconSize = IconSize.Small,
    val characterType: LabelType = LabelType.Label3,
    val backgroundColor: BackgroundColor = BackgroundColor.Foreground10,
  ) : ListItemAccessory {
    val text = character.toString()

    enum class BackgroundColor {
      Foreground10,
      SubtleBackground,
    }

    companion object {
      fun fromLetters(
        input: String,
        circleSize: IconSize = IconSize.Small,
        characterType: LabelType = LabelType.Label3,
        backgroundColor: BackgroundColor = BackgroundColor.Foreground10,
      ): CircularCharacterAccessory {
        return CircularCharacterAccessory(
          character = input.firstOrNull(Char::isLetter)?.uppercaseChar() ?: '?',
          circleSize = circleSize,
          characterType = characterType,
          backgroundColor = backgroundColor
        )
      }
    }
  }

  data class CircularIconAccessory(
    val icon: Icon,
    val circleSize: IconSize = IconSize.Small,
    val iconSize: IconSize = IconSize.Small,
    val iconTint: IconTint? = null,
    val backgroundColor: BackgroundColor = BackgroundColor.Foreground10,
  ) : ListItemAccessory {
    enum class BackgroundColor {
      Foreground10,
      SubtleBackground,
    }
  }

  data class ContactAvatarAccessory(
    val name: String,
    val isLoading: Boolean,
  ) : ListItemAccessory {
    val initials = name
      .split(' ')
      .mapNotNull { chunk ->
        chunk.firstOrNull(Char::isLetter)?.uppercaseChar()
      }
      .let { letters ->
        when {
          letters.isEmpty() -> "?"
          letters.size == 1 -> letters.single().toString()
          else -> "${letters.first()}${letters.last()}"
        }
      }
  }

  data class IconAccessory(
    /** The padding to apply to the icon on all sides  */
    val iconPadding: Int? = null,
    val opticalOffsetX: Int? = null,
    val model: IconModel,
    val onClick: (() -> Unit)? = null,
    val testTag: String? = null,
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

  data class CheckboxAccessory(
    val isChecked: Boolean,
    val onClick: () -> Unit,
    val isEnabled: Boolean = true,
    val testTag: String? = null,
  ) : ListItemAccessory

  /**
   * Common accessories.
   */
  companion object {
    fun drillIcon(
      tint: IconTint? = null,
      iconSize: IconSize = IconSize.Small,
      opticalOffsetX: Int? = null,
    ) = IconAccessory(
      iconPadding = null,
      opticalOffsetX = opticalOffsetX,
      model = IconModel(
        icon = Icon.CaretRight,
        iconSize = iconSize,
        iconBackgroundType = IconBackgroundType.Transient,
        iconTint = tint
      )
    )

    fun checkIcon(): ListItemAccessory =
      CheckboxAccessory(isChecked = true, onClick = {}, isEnabled = false)
  }
}
