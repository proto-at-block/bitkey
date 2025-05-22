package bitkey.f8e.verify

import bitkey.verification.TxVerificationPolicy
import bitkey.verification.VerificationThreshold
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Handles policy setting modifications for transaction verifications.
 */
interface TxVerifyPolicyF8eClient {
  /**
   * Set a new policy for transaction verification.
   */
  suspend fun setPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    threshold: VerificationThreshold,
  ): Result<TxVerificationPolicy, Throwable>
}
