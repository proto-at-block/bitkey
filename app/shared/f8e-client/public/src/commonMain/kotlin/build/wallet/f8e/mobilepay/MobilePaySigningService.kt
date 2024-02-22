package build.wallet.f8e.mobilepay

import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

/**
 * F8e service used to sign Bitcoin transactions with the f8e keyset. Meant to be used in
 * conjunction with signing psbt with the app keyset without the need for hardware - hence the
 * name "mobile pay".
 */
interface MobilePaySigningService {
  /**
   * Sends partially signed Bitcoin transaction (PSBT) to the server for signing with the keyset of the given ID,
   * and returns signed copy of the transaction.
   *
   * @property fullAccountId: The ID of the account building the transaction.
   * @property keysetId: The server id of the keyset you wish to sign with.
   * @property psbt: The half signed transaction to be signed by the server key before becoming a valid transaction.
   */
  suspend fun signWithSpecificKeyset(
    f8eEnvironment: F8eEnvironment,
    fullAccountId: FullAccountId,
    keysetId: String,
    psbt: Psbt,
  ): Result<Psbt, NetworkingError>
}
