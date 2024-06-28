package build.wallet.nfc

import build.wallet.catchingResult
import build.wallet.logging.LogLevel
import build.wallet.logging.NFC_TAG
import build.wallet.logging.log
import build.wallet.nfc.NfcSession.Parameters
import build.wallet.nfc.interceptors.NfcEffect
import build.wallet.nfc.interceptors.NfcTransactionInterceptor
import build.wallet.nfc.platform.NfcCommandsProvider
import build.wallet.nfc.platform.NfcSessionProvider
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapEither
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NfcTransactorImpl(
  private val commandsProvider: NfcCommandsProvider,
  private val sessionProvider: NfcSessionProvider,
  private val interceptors: List<NfcTransactionInterceptor>,
) : NfcTransactor {
  override var isTransacting: Boolean = false
    private set

  /**
   * A mutex used to ensure only one call to transact is in flight at a time.
   */
  private val nfcTransactionLock = Mutex(locked = false)

  override suspend fun <T> transact(
    parameters: Parameters,
    transaction: TransactionFn<T>,
  ): Result<T, NfcException> {
    log(tag = NFC_TAG) { "Starting an NFC Transaction" }

    // check if there is an ongoing transaction. if so, return an error
    if (nfcTransactionLock.isLocked) {
      log(tag = NFC_TAG, level = LogLevel.Warn) {
        "NFC transaction already in progress"
      }

      return Err(NfcException.TransactionInProgress())
    }

    return catchingResult {
      nfcTransactionLock.withLock {
        isTransacting = true
        sessionProvider.get(parameters).use { nfcSession ->
          CompletableDeferred<T>().also { result ->
            val effect: NfcEffect = { session, commands ->
              result.complete(transaction(session, commands))
            }

            val chain = interceptors.fold(effect) { acc, interceptor -> interceptor(acc) }
            chain(nfcSession, commandsProvider(parameters))
          }.await()
        }
      }
    }.mapEither(
      success = {
        isTransacting = false
        it
      },
      failure = {
        isTransacting = false
        it.asNfcException()
      }
    )
  }
}
