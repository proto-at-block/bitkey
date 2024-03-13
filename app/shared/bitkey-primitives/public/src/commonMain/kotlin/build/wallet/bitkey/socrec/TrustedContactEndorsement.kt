package build.wallet.bitkey.socrec

/**
 * Represents an endorsement of a Trusted Contact.
 *
 * @param recoveryRelationshipId associated with the Social Recovery relationship.
 * @param keyCertificate certificate that endorses a trusted contact (TC) key's authenticity.
 */
data class TrustedContactEndorsement(
  val recoveryRelationshipId: RecoveryRelationshipId,
  val keyCertificate: TrustedContactKeyCertificate,
)
