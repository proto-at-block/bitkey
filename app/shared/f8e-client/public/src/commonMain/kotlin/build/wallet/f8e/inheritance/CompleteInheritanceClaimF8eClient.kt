package build.wallet.f8e.inheritance

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import build.wallet.bitkey.inheritance.InheritanceClaimId
import com.github.michaelbull.result.Result

/**
 * Complete a partially signed inheritance claim by signing it on the server.
 */
interface CompleteInheritanceClaimF8eClient {
  /**
   * Uploads the partially signed transaction, signs and complete the transfer.
   */
  suspend fun completeInheritanceClaim(
    fullAccount: FullAccount,
    claimId: InheritanceClaimId,
    psbt: Psbt,
  ): Result<BeneficiaryClaim.CompleteClaim, Throwable>
}
