package build.wallet.bitkey.relationships

import build.wallet.crypto.PublicKey
import dev.zacsweers.redacted.annotations.Redacted

/**
 * The Trusted Contact's view of an invitation.
 */
data class IncomingInvitation(
  val relationshipId: String,
  @Redacted
  val code: String,
  val protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
  val recoveryRelationshipRoles: Set<TrustedContactRole>,
)
