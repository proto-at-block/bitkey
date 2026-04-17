package build.wallet.bitkey.hardware

/**
 * Validates that text is displayable on the hardware device screen.
 *
 * The hardware uses a monospace font that supports ASCII printable characters
 * and Latin Extended letters. Emoji, CJK, Cyrillic, and other non-Latin scripts
 * are not renderable and must be rejected so the user sees meaningful text on
 * the confirmation prompt.
 *
 * This utility is shared across features that bind user-supplied text into
 * action proofs (e.g. contact names, mobile pay labels).
 */
object HardwareDisplayValidation {
  /**
   * Maximum character length for a value displayed on the hardware screen.
   * Must fit in the firmware's 128-byte value buffer.
   */
  const val MAX_VALUE_LENGTH = 64

  /**
   * Returns true if [char] can be rendered on the hardware display.
   *
   * Allows: ASCII printable (space through tilde), Latin-1 Supplement letters
   * (À-ÿ, skipping control-like chars), Latin Extended-A, Latin Extended-B.
   * Rejects: emoji, CJK, Cyrillic, NBSP, soft hyphen, control chars.
   */
  fun isHwDisplayable(char: Char): Boolean {
    // ASCII printable range (space through tilde)
    if (char in ' '..'~') return true
    // Latin-1 Supplement letters: accented characters (À-ÿ)
    // Starts at U+00C0 to skip control-like chars (U+00A0 NBSP, U+00AD soft hyphen)
    if (char in '\u00C0'..'\u00FF') return true
    // Latin Extended-A (e.g. Ā, ă, Ő)
    if (char in '\u0100'..'\u017F') return true
    // Latin Extended-B (e.g. ǅ)
    if (char in '\u0180'..'\u024F') return true
    return false
  }

  /**
   * Returns true if the entire [text] is displayable on the hardware screen
   * (within length limits and all characters renderable).
   *
   * Empty/blank text is considered displayable (callers handle empty separately).
   */
  fun isHwDisplayable(text: String): Boolean {
    val trimmed = text.trim { it <= ' ' }
    return trimmed.isEmpty() ||
      (trimmed.length <= MAX_VALUE_LENGTH && trimmed.all { isHwDisplayable(it) })
  }
}
