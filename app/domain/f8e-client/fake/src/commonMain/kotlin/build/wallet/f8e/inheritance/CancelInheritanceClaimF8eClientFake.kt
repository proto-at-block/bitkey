package build.wallet.f8e.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.*
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CancelInheritanceClaimF8eClientFake(
  var response: Result<BeneficiaryClaim.CanceledClaim, Throwable> =
    Ok(BeneficiaryCanceledClaimFake),
) : CancelInheritanceClaimF8eClient {
  override suspend fun cancelClaim(
    fullAccount: FullAccount,
    inheritanceClaim: InheritanceClaim,
  ): Result<InheritanceClaim, Throwable> = response
}
