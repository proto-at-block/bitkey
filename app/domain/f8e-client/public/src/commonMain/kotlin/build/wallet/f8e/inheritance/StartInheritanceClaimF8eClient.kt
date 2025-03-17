package build.wallet.f8e.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.relationships.RelationshipId
import com.github.michaelbull.result.Result

/**
 * Initiates an inheritance claim for a beneficiary.
 */
interface StartInheritanceClaimF8eClient {
  /**
   * Start an inheritance claim for the given [relationshipId]
   */
  suspend fun startInheritanceClaim(
    fullAccount: FullAccount,
    relationshipId: RelationshipId,
  ): Result<BeneficiaryClaim.PendingClaim, Throwable>
}
