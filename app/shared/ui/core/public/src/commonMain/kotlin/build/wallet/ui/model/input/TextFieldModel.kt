package build.wallet.ui.model.input

/**
 * Defines an input field for text
 *
 * @property value - the current value of the input field
 * @property selectionOverride - an override of the text selection (which controls cursor position).
 * Used in circumstances where the value is being formatted and we want to make sure the correct
 * cursor position is retained after formatting.
 * @property placeholderText - the placeholder label text of the input field
 * @property onValueChange - change handler for updating the value and selection of the input field
 * @property keyboardType - the keyboard that should be used for this input field
 * @property masksText - whether the entered text should be masked or not
 * @property enableAutoCorrect - controls whether the input field will include OS autocorrect
 * @property capitalization - defines the capitalization behavior of entered text
 * @property onDone - action taken when keyboard done is invoked
 * @property focusByDefault - if keyboard should be active for input field by default
 */
data class TextFieldModel(
  val value: String = "",
  val selectionOverride: IntRange? = null,
  val placeholderText: String,
  val onValueChange: (String, IntRange) -> Unit,
  val keyboardType: KeyboardType,
  val masksText: Boolean = false,
  val enableAutoCorrect: Boolean = false,
  val capitalization: Capitalization = Capitalization.None,
  val onDone: (() -> Unit)? = null,
  val focusByDefault: Boolean = true,
  val maxLength: Int? = null,
) {
  enum class KeyboardType {
    /** Default keyboard */
    Default,

    /** Keyboard optimized for decimal number entry */
    Decimal,

    /** Keyboard optimized for email entry */
    Email,

    /** Keyboard optimized for number entry */
    Number,

    /** Keyboard optimized for phone number entry */
    Phone,

    /** Keyboard optimized for uri entry */
    Uri,
  }

  /** The keyboard capitalization defaults, implemented using each platform's UI kit. */
  enum class Capitalization {
    /**
     * Do not auto-capitalize text.
     */
    None,

    /**
     * Capitalize all characters.
     */
    Characters,

    /**
     * Capitalize the first character of every word.
     */
    Words,

    /**
     * Capitalize the first character of each sentence.
     */
    Sentences,
  }
}
