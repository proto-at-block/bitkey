package build.wallet.f8e.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.InheritanceClaim
import com.github.michaelbull.result.Result

/**
 * Promotes inheritance claims to a locked state.
 */
interface CancelInheritanceClaimF8eClient {
  /**
   * Cancels an inheritance claim
   */
  suspend fun cancelClaim(
    fullAccount: FullAccount,
    inheritanceClaim: InheritanceClaim,
  ): Result<InheritanceClaim, Throwable>
}
