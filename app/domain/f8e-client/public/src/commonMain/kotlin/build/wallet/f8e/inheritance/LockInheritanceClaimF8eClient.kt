package build.wallet.f8e.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.challange.SignedChallenge.AppSignedChallenge
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaimId
import build.wallet.bitkey.relationships.RelationshipId
import com.github.michaelbull.result.Result

/**
 * Promotes inheritance claims to a locked state.
 */
interface LockInheritanceClaimF8eClient {
  /**
   * Locks an inheritance claim to the current wallet address after the delay
   * period expires.
   *
   * This operation finalizes the delay process and reveals the keys necessary
   * to display and subsequently sign a transaction.
   */
  suspend fun lockClaim(
    fullAccount: FullAccount,
    relationshipId: RelationshipId,
    inheritanceClaimId: InheritanceClaimId,
    signedChallenge: AppSignedChallenge,
  ): Result<BeneficiaryClaim.LockedClaim, Throwable>
}
