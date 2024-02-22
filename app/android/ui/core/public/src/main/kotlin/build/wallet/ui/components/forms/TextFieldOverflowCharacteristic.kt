package build.wallet.ui.components.forms

import androidx.compose.ui.unit.TextUnit

/**
 * Defines a set of text overflow behaviours for TextField.
 */
sealed class TextFieldOverflowCharacteristic {
  /**
   * A single-line text field that truncates the head of the string as you type and the text
   * overflows, so the text field will show up as "..defghijk".
   */
  data object Truncate : TextFieldOverflowCharacteristic()

  /**
   * An TextField that automatically resizes the text font to fit the parameters specified by the
   * associated values.
   */
  data class Resize(
    val maxLines: Int,
    val minFontSize: TextUnit,
    val scaleFactor: Float,
  ) : TextFieldOverflowCharacteristic()

  /**
   * Lets text in the TextField automatically "wrap" into new lines.
   */
  data object Multiline : TextFieldOverflowCharacteristic()
}
