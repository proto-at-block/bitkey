package build.wallet.ui.model.button

import build.wallet.statemachine.core.Icon
import build.wallet.ui.model.Click
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Treatment.Black
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary
import build.wallet.ui.model.icon.IconSize

/**
 * Defines schema for button component.
 *
 * https://www.figma.com/file/aaOrQTgHXp2NpOYCBDoe5E/Wallet-System?node-id=1657%3A1952&t=4Pm1P09JT5e0mxqa-1
 */
data class ButtonModel(
  val text: String,
  val isEnabled: Boolean = true,
  val isLoading: Boolean = false,
  val leadingIcon: Icon? = null,
  val treatment: Treatment = Primary,
  val size: Size,
  val testTag: String? = null,
  val onClick: Click,
) {
  enum class Treatment {
    // Styled with an opaque green background and white text
    Primary,

    // Styled with a translucent lighter background and black text
    Secondary,

    // Styled with a translucent lighter background and red text
    SecondaryDestructive,

    // Styled with no background and black text with an underline
    Tertiary,

    // Styled with no background and black text with no underline
    TertiaryNoUnderline,

    // Styled with no background and green text with an underline
    TertiaryPrimary,

    // Styled with no background and green text with no underline
    TertiaryPrimaryNoUnderline,

    // Styled with no background and red text
    TertiaryDestructive,

    // Styled with a translucent background (white 20% opacity) and white text. Normal translucent style.
    Translucent,

    // Styled with a translucent background (white 10% opacity) and white text. Lighter translucent style.
    Translucent10,

    // Styled with an opaque black background and white text
    Black,

    // Styled with an opaque white background and black text
    White,

    // Styled with an opaque orange background and white text
    Warning,
    ;

    val leadingIconSize: IconSize
      get() =
        when (this) {
          Tertiary,
          TertiaryPrimary,
          TertiaryPrimaryNoUnderline,
          TertiaryNoUnderline,
          TertiaryDestructive,
          ->
            IconSize.XSmall
          else ->
            IconSize.Accessory
        }
  }

  enum class Size {
    Regular,
    Compact,
    Footer,
    Floating,
    FitContent,
  }

  /**
   * Convenience for creating a button model where the action *might* require interaction with
   * the Bitkey device
   */
  constructor(
    text: String,
    requiresBitkeyInteraction: Boolean,
    treatment: Treatment,
    leadingIcon: Icon? = null,
    isEnabled: Boolean = true,
    isLoading: Boolean = false,
    size: Size,
    testTag: String? = null,
    onClick: () -> Unit,
  ) : this(
    text = text,
    treatment = if (requiresBitkeyInteraction) Black else treatment,
    size = size,
    leadingIcon = if (requiresBitkeyInteraction) Icon.SmallIconBitkey else leadingIcon,
    isEnabled = isEnabled,
    isLoading = isLoading,
    testTag = testTag,
    onClick = StandardClick { onClick() }
  )

  companion object {
    /**
     * Convenience for creating a button model where the action requires interaction with
     * the Bitkey device
     */
    fun BitkeyInteractionButtonModel(
      text: String,
      isEnabled: Boolean = true,
      isLoading: Boolean = false,
      size: Size,
      testTag: String? = null,
      onClick: () -> Unit,
    ) = ButtonModel(
      text = text,
      treatment = Black,
      size = size,
      leadingIcon = Icon.SmallIconBitkey,
      isEnabled = isEnabled,
      isLoading = isLoading,
      testTag = testTag,
      onClick = StandardClick { onClick() }
    )
  }
}
