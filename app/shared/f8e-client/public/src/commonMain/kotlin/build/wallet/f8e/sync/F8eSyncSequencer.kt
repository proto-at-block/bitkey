package build.wallet.f8e.sync

import build.wallet.bitkey.account.Account
import build.wallet.logging.LogLevel.Error
import build.wallet.logging.log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class F8eSyncSequencer {
  private val syncLock = Mutex()
  private val dataLock = Mutex()
  private var currentAccount: Account? = null

  /**
   * Sequences multiple invocations of [task] from different coroutines. If a
   * previous invocation of [task] is still running, and it had a different [Account] as the
   * current invocation, an [IllegalStateException] will be thrown. Otherwise, this call
   * will suspend until the previous invocation is canceled.
   */
  suspend fun run(
    account: Account,
    task: suspend () -> Unit,
  ) {
    val lastAccount = dataLock.withLock { currentAccount }
    val acquired = syncLock.tryLock()

    if (acquired) {
      try {
        doLocked(account, task)
      } finally {
        syncLock.unlock()
      }
      return
    }

    if (lastAccount != null && lastAccount != account) {
      log(Error) {
        "sync sequencer called with different account while previous coroutine was active. " +
          "Last account: $lastAccount, new account: $account"
      }
    } else {
      syncLock.withLock { doLocked(account, task) }
    }
  }

  private suspend fun doLocked(
    account: Account,
    task: suspend () -> Unit,
  ) {
    dataLock.withLock { currentAccount = account }
    task()
  }
}
