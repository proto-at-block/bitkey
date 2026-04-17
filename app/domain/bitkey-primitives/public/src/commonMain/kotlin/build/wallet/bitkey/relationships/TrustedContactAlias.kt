package build.wallet.bitkey.relationships

import build.wallet.bitkey.hardware.HardwareDisplayValidation
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Corresponds to a unique alias for a [EndorsedTrustedContact].
 */
@Serializable
@JvmInline
@Redacted
value class TrustedContactAlias(val alias: String) {
  companion object {
    /**
     * Maximum length for a contact name.
     * Delegates to [HardwareDisplayValidation.MAX_VALUE_LENGTH].
     */
    const val MAX_LENGTH = HardwareDisplayValidation.MAX_VALUE_LENGTH

    /**
     * Validates that a contact name is displayable on the hardware device.
     *
     * The hardware uses a monospace font that supports letters, digits, spaces,
     * and common punctuation. Emoji and special symbols are not renderable and
     * must be rejected so the user sees a meaningful confirmation prompt.
     *
     * @return [Ok] if the name is valid, or [Err] with a user-facing error message if invalid.
     */
    fun validate(name: String): Result<Unit, String> {
      val trimmed = name.trim { it <= ' ' }
      if (trimmed.isEmpty()) return Ok(Unit) // empty is handled separately by the "Continue" button
      if (trimmed.length > MAX_LENGTH) {
        return Err("Name must be $MAX_LENGTH characters or fewer")
      }
      if (!trimmed.all { HardwareDisplayValidation.isHwDisplayable(it) }) {
        return Err("Name can only contain letters, numbers, spaces, and punctuation")
      }
      return Ok(Unit)
    }
  }
}
