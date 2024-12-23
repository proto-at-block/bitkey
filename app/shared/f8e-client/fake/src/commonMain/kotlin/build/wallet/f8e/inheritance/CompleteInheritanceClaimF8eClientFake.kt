package build.wallet.f8e.inheritance

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.BeneficiaryCompleteClaimFake
import build.wallet.bitkey.inheritance.InheritanceClaimId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CompleteInheritanceClaimF8eClientFake(
  var response: Result<BeneficiaryClaim.CompleteClaim, Throwable> =
    Ok(BeneficiaryCompleteClaimFake),
) : CompleteInheritanceClaimF8eClient {
  override suspend fun completeInheritanceClaim(
    fullAccount: FullAccount,
    claimId: InheritanceClaimId,
    psbt: Psbt,
  ): Result<BeneficiaryClaim.CompleteClaim, Throwable> {
    return response
  }
}
