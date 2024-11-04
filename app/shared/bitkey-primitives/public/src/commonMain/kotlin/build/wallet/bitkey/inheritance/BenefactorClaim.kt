package build.wallet.bitkey.inheritance

import build.wallet.bitkey.relationships.RelationshipId
import kotlinx.datetime.Instant

/**
 * Inheritance Claim as viewed from the benefactor's side.
 */
sealed interface BenefactorClaim : InheritanceClaim {
  /**
   * Inheritance claims that have started, but not passed their delay period.
   */
  data class PendingClaim(
    override val claimId: InheritanceClaimId,
    override val relationshipId: RelationshipId,
    val delayEndTime: Instant,
    val delayStartTime: Instant,
  ) : BenefactorClaim

  /**
   * Inheritance claims that have been cancelled before being completed.
   */
  data class CanceledClaim(
    override val claimId: InheritanceClaimId,
    override val relationshipId: RelationshipId,
  ) : BenefactorClaim

  /**
   * Inheritance claims that have completed their delay period and have
   * subsequently been locked by the beneficiary.
   */
  data class LockedClaim(
    override val claimId: InheritanceClaimId,
    override val relationshipId: RelationshipId,
  ) : BenefactorClaim

  /**
   * An inheritance claim that is in an unknown state.
   *
   * This type is included for forwards compatibility, and forces the
   * caller to handle claims that may be in an undefined state to this
   * application version without failing at deserialization.
   *
   * Construction of this type is hidden, as this type should only
   * be the result of deserialization.
   */
  data class UnknownStatus internal constructor(
    override val claimId: InheritanceClaimId,
    override val relationshipId: RelationshipId,
    val status: InheritanceClaimStatus,
  ) : BenefactorClaim
}
