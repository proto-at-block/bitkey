package build.wallet.f8e.inheritance

import app.cash.turbine.Turbine
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.challange.SignedChallenge.AppSignedChallenge
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.BeneficiaryLockedClaimFake
import build.wallet.bitkey.inheritance.InheritanceClaimId
import build.wallet.bitkey.relationships.RelationshipId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class LockInheritanceClaimF8eClientFake(
  val calls: Turbine<InheritanceClaimId>,
  var response: Result<BeneficiaryClaim.LockedClaim, Throwable> = Ok(BeneficiaryLockedClaimFake),
) : LockInheritanceClaimF8eClient {
  override suspend fun lockClaim(
    fullAccount: FullAccount,
    relationshipId: RelationshipId,
    inheritanceClaimId: InheritanceClaimId,
    signedChallenge: AppSignedChallenge,
  ): Result<BeneficiaryClaim.LockedClaim, Throwable> {
    calls.add(inheritanceClaimId)
    return response
  }
}
