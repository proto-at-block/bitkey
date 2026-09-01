package bitkey.f8e.account

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onSuccess

class WalletMetadataBackupF8eClientFake : WalletMetadataBackupF8eClient {
  var storedBackup: WalletMetadataBackup? = null
  var getResult: Result<WalletMetadataBackup?, NetworkingError>? = null
  var putResult: Result<Unit, NetworkingError> = Ok(Unit)
  val getCalls = mutableListOf<FullAccountId>()
  val getEnvironments = mutableListOf<F8eEnvironment>()
  val putCalls = mutableListOf<Pair<FullAccountId, WalletMetadataBackup>>()
  val putEnvironments = mutableListOf<F8eEnvironment>()

  /** Invoked while a get is "in flight", before it returns. Lets tests simulate concurrent edits. */
  var onGet: (suspend () -> Unit)? = null

  /** Invoked while a put is "in flight", before it returns. Lets tests simulate concurrent edits. */
  var onPut: (suspend () -> Unit)? = null

  override suspend fun get(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
  ): Result<WalletMetadataBackup?, NetworkingError> {
    getCalls += accountId
    getEnvironments += f8eEnvironment
    onGet?.invoke()
    return getResult ?: Ok(storedBackup)
  }

  override suspend fun put(
    f8eEnvironment: F8eEnvironment,
    accountId: FullAccountId,
    backup: WalletMetadataBackup,
  ): Result<Unit, NetworkingError> {
    putCalls += accountId to backup
    putEnvironments += f8eEnvironment
    onPut?.invoke()
    putResult.onSuccess { storedBackup = backup }
    return putResult
  }

  fun reset() {
    storedBackup = null
    getResult = null
    putResult = Ok(Unit)
    getCalls.clear()
    getEnvironments.clear()
    putCalls.clear()
    putEnvironments.clear()
    onGet = null
    onPut = null
  }
}
