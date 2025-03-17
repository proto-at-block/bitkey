package build.wallet.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class InheritanceTransactionFactoryMock(
  var createResponse: Result<InheritanceTransactionDetails, Throwable> =
    Ok(InheritanceTransactionDetailsFake),
) : InheritanceTransactionFactory {
  override suspend fun createFullBalanceTransaction(
    account: FullAccount,
    claim: BeneficiaryClaim.LockedClaim,
  ): Result<InheritanceTransactionDetails, Throwable> {
    return createResponse
  }
}
