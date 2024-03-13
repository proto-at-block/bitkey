package build.wallet.bitkey.socrec

/**
 * The Trusted Contact's view of an invitation.
 */
data class IncomingInvitation(
  val recoveryRelationshipId: String,
  val code: String,
  val protectedCustomerEnrollmentPakeKey: ProtectedCustomerEnrollmentPakeKey,
)
