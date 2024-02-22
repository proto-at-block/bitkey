package build.wallet.bitkey.socrec

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Represents an invitation to become a Trusted Contact for a Protected Customer.
 *
 * Used in the Protected Customer experience to represent the invite being created / pending / expired
 * as well as in the Trusted Contact experience to represent the invite being retrieved / accepted.
 *
 * @param trustedContactAlias: The alias of the contact being requested to offer protection with
 *  this invitation. Note: unused and just a blank string when [Invitation] is used in the context
 *  of the Trusted Contact experience
 */
data class Invitation(
  override val recoveryRelationshipId: String,
  override val trustedContactAlias: TrustedContactAlias,
  val token: String,
  val expiresAt: Instant,
) : RecoveryContact {
  fun isExpired(clock: Clock) = expiresAt < clock.now()

  fun isExpired(now: Long) = expiresAt.toEpochMilliseconds() < now
}
