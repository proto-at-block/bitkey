package build.wallet.bitkey.relationships

import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents the Protected Customer's view of an invitation to become a Recovery Contact.
 *
 * Used in the Protected Customer experience to represent the invite being created / pending / expired
 * as well as in the Recovery Contact experience to represent the invite being retrieved / accepted.
 *
 * @param trustedContactAlias The alias of the contact being requested to offer protection with
 *  this invitation. Note: unused and just a blank string when [Invitation] is used in the context
 *  of the Recovery Contact experience
 *  @param code the code generated by f8e to be concatenated with the PAKE part to form
 *  the full invite code. The RC will use this code to accept the invitation. The code is always
 *  in hex and left justified.
 *  @param codeBitLength [code] length in bits.
 */
data class Invitation(
  @Deprecated("Use typed ID", ReplaceWith("id"))
  override val relationshipId: String,
  override val trustedContactAlias: TrustedContactAlias,
  override val roles: Set<TrustedContactRole>,
  @Redacted
  val code: String,
  val codeBitLength: Int,
  val expiresAt: Instant,
) : TrustedContact {
  fun isExpired(clock: Clock) = expiresAt < clock.now()

  fun isExpired(now: Long) = expiresAt.toEpochMilliseconds() < now
}

fun List<Invitation>.socialRecoveryInvitations(): List<Invitation> {
  return this.filter { it.roles.contains(TrustedContactRole.SocialRecoveryContact) }
}
