package build.wallet.f8e.mobilepay

import bitkey.verification.TxVerificationApproval
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result

/**
 * F8e service used to sign Bitcoin transactions with the f8e keyset. Meant to be used in
 * conjunction with signing psbt with the app keyset without the need for hardware - hence the
 * name "mobile pay".
 */
interface MobilePaySigningF8eClient {
  /**
   * Sends partially signed Bitcoin transaction (PSBT) to the server for signing with the keyset of the given ID,
   * and returns signed copy of the transaction.
   *
   * @param fullAccountId The ID of the account building the transaction.
   * @param keysetId The server id of the keyset you wish to sign with.
   * @param psbt The half signed transaction to be signed by the server key before becoming a valid transaction.
   * @param grant If verification was completed for this transaction, the resulting grant can be passed to sign the transaction.
   */
  suspend fun signWithSpecificKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
    grant: TxVerificationApproval? = null,
  ): Result<Psbt, Error>
}
