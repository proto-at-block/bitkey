package bitkey.verification

import build.wallet.f8e.auth.HwFactorProofOfPossession
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Provides domain logic for managing transaction verification policies.
 */
interface TxVerificationService {
  /**
   * The current transaction verification policy in effect.
   */
  fun getCurrentThreshold(): Flow<Result<VerificationThreshold, Error>>

  /**
   * Get an optional policy that is waiting for authorization to be completed.
   */
  fun getPendingPolicy(): Flow<Result<TxVerificationPolicy.Pending?, Error>>

  /**
   * Update the transaction limit to the specified threshold.
   *
   * @param verificationThreshold The new amount to limit transactions without verification to.
   * @return a verification policy, either active or pending depending on whether
   *         the policy required authorization.
   */
  suspend fun updateThreshold(
    verificationThreshold: VerificationThreshold,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<TxVerificationPolicy, Error>
}
