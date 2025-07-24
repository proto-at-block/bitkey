package bitkey.verification

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRate
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Provides domain logic for managing transaction verification policies.
 */
interface TxVerificationService {
  /**
   * The current transaction verification policy in effect.
   */
  fun getCurrentThreshold(): Flow<Result<VerificationThreshold?, Error>>

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
    policy: TxVerificationPolicy.Active,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, Error>

  /**
   * Attempts to determine if transaction verification will be necessary to sent the amount given.
   *
   * Note: This method may not be correct 100% of the time, or agree with the server,
   * but can be used to quickly determine whether verification is likely required
   * without starting a server request.
   */
  suspend fun isVerificationRequired(
    amount: BitcoinMoney,
    exchangeRates: List<ExchangeRate>?,
  ): Boolean

  /**
   * Starts a verification request for the transaction.
   *
   * @return a flow with state updates on the verification's confirmation progress.
   */
  suspend fun requestVerification(
    psbt: Psbt,
  ): Result<ConfirmationFlow<TxVerificationApproval>, Throwable>

  /**
   * Request that the server signs a hardware grant without a verification request.
   *
   * This should be used when the app believes that no verification is required.
   */
  suspend fun requestGrant(psbt: Psbt): Result<TxVerificationApproval, Throwable>
}
