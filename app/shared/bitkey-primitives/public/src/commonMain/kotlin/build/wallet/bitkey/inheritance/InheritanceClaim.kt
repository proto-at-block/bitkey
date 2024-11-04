package build.wallet.bitkey.inheritance

import build.wallet.bitkey.relationships.RelationshipId

/**
 * Common data used in inheritance claims on either the benefactor or
 * beneficiary view.
 */
sealed interface InheritanceClaim {
  /**
   * Unique ID for the claim.
   */
  val claimId: InheritanceClaimId

  /**
   * The ID of the Trusted Contact relationship for which this claim is made.
   */
  val relationshipId: RelationshipId
}
