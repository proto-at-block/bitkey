package build.wallet.bitkey.socrec

import build.wallet.crypto.PublicKey
import dev.zacsweers.redacted.annotations.Redacted

/**
 * The Trusted Contact's view of an invitation.
 */
data class IncomingInvitation(
  val recoveryRelationshipId: String,
  @Redacted
  val code: String,
  val protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
)
