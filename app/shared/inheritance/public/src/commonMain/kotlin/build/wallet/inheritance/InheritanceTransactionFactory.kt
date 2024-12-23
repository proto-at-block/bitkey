package build.wallet.inheritance

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.inheritance.BeneficiaryClaim
import com.github.michaelbull.result.Result

/**
 * Generates all the data required for an inheritance transaction.
 */
interface InheritanceTransactionFactory {
  /**
   * Create a partially signed inheritance transaction.
   *
   * This transaction will be locally signed, but not broadcast to the
   * server by this factory.
   */
  suspend fun createFullBalanceTransaction(
    account: FullAccount,
    claim: BeneficiaryClaim.LockedClaim,
  ): Result<InheritanceTransactionDetails, Throwable>
}
