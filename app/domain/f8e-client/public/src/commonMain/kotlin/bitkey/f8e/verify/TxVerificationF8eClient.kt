package bitkey.f8e.verify

import bitkey.verification.PendingTransactionVerification
import bitkey.verification.TxVerificationApproval
import bitkey.verification.TxVerificationId
import bitkey.verification.TxVerificationState
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.BitcoinDisplayUnit
import com.github.michaelbull.result.Result

/**
 * API interactions for managing Transaction Verification requests to the server.
 */
interface TxVerificationF8eClient {
  /**
   * Start the verification process for the specified transaction.
   *
   * @param psbt The transaction to be signed in the resulting grant after verification.
   * @param fiatCurrency The fiat currency used to display the transaction amount in the verification UI.
   * @param bitcoinDisplayUnit Used to control the server-verification UI so that it matches the local preferences.
   * @param useBip177 Whether to use BIP 177 Bitcoin sign (₿) instead of "sats" text.
   */
  suspend fun createVerificationRequest(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    fiatCurrency: FiatCurrency,
    bitcoinDisplayUnit: BitcoinDisplayUnit,
    useBip177: Boolean,
  ): Result<PendingTransactionVerification, Throwable>

  /**
   * Get the current state of a specified transaction verification.
   *
   * @param verificationId The ID of the transaction verification to check.
   */
  suspend fun getVerificationStatus(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    verificationId: TxVerificationId,
  ): Result<TxVerificationState, Throwable>

  /**
   * Cancel a pending transaction verification request.
   *
   * This will invalidate the in-progress verification to avoid confusion
   * for the user by deactivating the link in the email sent to their account.
   *
   * Note: It's important to not block the user if this call fails.
   * Verifications will eventually expire, so this operation is not critical.
   */
  suspend fun cancelVerification(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    verificationId: TxVerificationId,
  ): Result<Unit, Throwable>

  /**
   * Request a hardware grant for the specified transaction without verification.
   *
   * @param psbt The transaction to be signed. This must be under the transaction limit amount.
   * @param fiatCurrency irrelevant, but required by the API.
   * @param bitcoinDisplayUnit irrelevant, but required by the API.
   * @param useBip177 irrelevant, but required by the API.
   */
  suspend fun requestGrant(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    fiatCurrency: FiatCurrency,
    bitcoinDisplayUnit: BitcoinDisplayUnit,
    useBip177: Boolean,
  ): Result<TxVerificationApproval, Throwable>
}
