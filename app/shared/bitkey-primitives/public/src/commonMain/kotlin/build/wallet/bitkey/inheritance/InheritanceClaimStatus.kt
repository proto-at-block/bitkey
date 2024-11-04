package build.wallet.bitkey.inheritance

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Possible states for an inheritance claim to be in.
 *
 * This type is implemented as a non-exhaustive enumeration to allow
 * forwards compatibility. Unknown states should be handled by the caller
 * and will not fail at serialization time.
 */
@Serializable
@JvmInline
value class InheritanceClaimStatus(val key: String) {
  companion object {
    val PENDING = InheritanceClaimStatus("PENDING")
    val CANCELED = InheritanceClaimStatus("CANCELED")
    val LOCKED = InheritanceClaimStatus("LOCKED")
  }
}
