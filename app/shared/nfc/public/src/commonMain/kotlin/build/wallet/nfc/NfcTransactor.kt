package build.wallet.nfc

import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.Result

/**
 * This class is the thin-waist for all application code to make NFC operations via its transact method.
 * It has the platform [NfcSession] and [NfcCommands] injected at creation.
 **/

typealias TransactionFn<T> = suspend (NfcSession, NfcCommands) -> T

interface NfcTransactor {
  suspend fun <T> transact(
    parameters: NfcSession.Parameters,
    transaction: TransactionFn<T>,
  ): Result<T, NfcException>
}
