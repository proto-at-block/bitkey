package build.wallet.bitkey.relationships

/**
 * Represents an endorsement of a Recovery Contact.
 *
 * @param relationshipId associated with the Social Recovery relationship.
 * @param keyCertificate certificate that endorses a Recovery Contact (RC) key's authenticity.
 */
data class TrustedContactEndorsement(
  val relationshipId: RelationshipId,
  val keyCertificate: TrustedContactKeyCertificate,
)
