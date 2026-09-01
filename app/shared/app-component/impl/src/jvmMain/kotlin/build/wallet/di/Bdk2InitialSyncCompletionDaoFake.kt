package build.wallet.di

import build.wallet.bitcoin.wallet.Bdk2InitialSyncCompletionDao
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class Bdk2InitialSyncCompletionDaoFake(
  private var defaultComplete: Boolean = true,
) : Bdk2InitialSyncCompletionDao {
  private val completions = mutableMapOf<String, Boolean>()
  val isDefaultComplete: Boolean
    get() = defaultComplete

  override suspend fun isComplete(walletIdentifier: String): Result<Boolean, Throwable> =
    Ok(completions[walletIdentifier] ?: defaultComplete)

  override suspend fun markComplete(walletIdentifier: String): Result<Unit, Throwable> {
    completions[walletIdentifier] = true
    return Ok(Unit)
  }

  fun markIncompleteByDefault() {
    setDefaultComplete(false)
  }

  fun setDefaultComplete(defaultComplete: Boolean) {
    this.defaultComplete = defaultComplete
    completions.clear()
  }

  fun reset() {
    setDefaultComplete(true)
  }
}
