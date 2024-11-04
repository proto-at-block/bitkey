package build.wallet.f8e.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.BeneficiaryPendingClaimFake
import build.wallet.bitkey.relationships.RelationshipId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class StartInheritanceClaimF8eFake(
  var response: Result<BeneficiaryClaim.PendingClaim, Throwable> = Ok(BeneficiaryPendingClaimFake),
) : StartInheritanceClaimF8eClient {
  override suspend fun startInheritanceClaim(
    fullAccount: FullAccount,
    relationshipId: RelationshipId,
  ): Result<BeneficiaryClaim.PendingClaim, Throwable> {
    return response
  }
}
