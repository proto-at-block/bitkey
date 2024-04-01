package build.wallet.nfc

import build.wallet.nfc.NfcSession.Parameters
import build.wallet.nfc.interceptors.NfcEffect
import build.wallet.nfc.interceptors.NfcTransactionInterceptor
import build.wallet.nfc.platform.NfcCommandsProvider
import build.wallet.nfc.platform.NfcSessionProvider
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.CompletableDeferred

class NfcTransactorImpl(
  private val commandsProvider: NfcCommandsProvider,
  private val sessionProvider: NfcSessionProvider,
  private val interceptors: List<NfcTransactionInterceptor>,
) : NfcTransactor {
  override suspend fun <T> transact(
    parameters: Parameters,
    transaction: TransactionFn<T>,
  ) = runSuspendCatching {
    sessionProvider.get(parameters).use { nfcSession ->
      CompletableDeferred<T>().also { result ->
        val effect: NfcEffect = { session, commands ->
          result.complete(transaction(session, commands))
        }

        val chain = interceptors.fold(effect) { acc, interceptor -> interceptor(acc) }
        chain(nfcSession, commandsProvider(parameters))
      }.await()
    }
  }.mapError { it.asNfcException() }
}
