package build.wallet.bitkey.socrec

import build.wallet.crypto.PublicKey

/**
 * The Trusted Contact's view of an invitation.
 */
data class IncomingInvitation(
  val recoveryRelationshipId: String,
  val code: String,
  val protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
)
