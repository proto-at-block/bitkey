package build.wallet.bitkey.inheritance

import build.wallet.bitkey.relationships.RelationshipId
import kotlinx.datetime.Instant

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
   * The ID of the Recovery Contact relationship for which this claim is made.
   */
  val relationshipId: RelationshipId
}

/**
 * Whether a claim is currently in a state that can be completed by
 * the beneficiary.
 */
fun InheritanceClaim.isApproved(now: Instant): Boolean {
  return when (this) {
    is BenefactorClaim.LockedClaim,
    is BeneficiaryClaim.LockedClaim,
    -> true
    is BeneficiaryClaim.PendingClaim -> delayEndTime <= now
    is BenefactorClaim.PendingClaim -> delayEndTime <= now

    else -> false
  }
}

/**
 * Whether the claim is in a state that can be canceled by either party.
 *
 * Note: This is different than [isActive], as not all active states are
 * cancelable (e.g. locked claims)
 */
val InheritanceClaim.isCancelable: Boolean
  get() {
    return when (this) {
      is BenefactorClaim.PendingClaim,
      is BeneficiaryClaim.PendingClaim,
      -> true
      else -> false
    }
  }

/**
 * Whether the claim is in a non-terminal state.
 */
val InheritanceClaim.isActive: Boolean
  get() {
    return when (this) {
      is BenefactorClaim.PendingClaim,
      is BeneficiaryClaim.PendingClaim,
      is BenefactorClaim.LockedClaim,
      is BeneficiaryClaim.LockedClaim,
      -> true
      else -> false
    }
  }

/**
 * Whether the claim terminated successfully.
 */
val InheritanceClaim.isCompleted: Boolean
  get() {
    return when (this) {
      is BenefactorClaim.CompleteClaim,
      is BeneficiaryClaim.CompleteClaim,
      -> true
      else -> false
    }
  }
