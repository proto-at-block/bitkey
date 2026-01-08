package bitkey.f8e.verify

import bitkey.f8e.privilegedactions.OptionalPrivilegedActionsF8eClient
import bitkey.verification.TxVerificationPolicy
import bitkey.verification.VerificationThreshold
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * Handles policy setting modifications for transaction verifications.
 */
interface TxVerifyPolicyF8eClient : OptionalPrivilegedActionsF8eClient<TxVerificationUpdateRequest, VerificationThreshold> {
  /**
   * Fetch the current active policy for transaction verification.
   */
  suspend fun getPolicy(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
  ): Result<TxVerificationPolicy?, Error>
}
