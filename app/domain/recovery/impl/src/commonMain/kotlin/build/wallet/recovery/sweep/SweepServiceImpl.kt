package build.wallet.recovery.sweep

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.activity.TransactionActivityOperations
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.worker.RefreshOperationFilter
import build.wallet.worker.RunStrategy
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class SweepServiceImpl(
  private val accountService: AccountService,
  private val sweepGenerator: SweepGenerator,
  sweepSyncFrequency: SweepSyncFrequency,
) : SweepService, SweepSyncWorker {
  override val runStrategy: Set<RunStrategy> = setOf(
    RunStrategy.Startup(),
    RunStrategy.Refresh(
      type = RefreshOperationFilter.Subset(TransactionActivityOperations)
    ),
    RunStrategy.Periodic(
      interval = sweepSyncFrequency.value
    )
  )

  /**
   * Caches the value of the sweep required flag. Synced by the [executeWork].
   */
  override val sweepRequired = MutableStateFlow(false)

  override suspend fun checkForSweeps() {
    sweepRequired.value = isSweepRequired()
  }

  override suspend fun executeWork() {
    sweepRequired.value = isSweepRequired()
  }

  /**
   * Returns `true` if customer should perform a sweep transaction.
   */
  private suspend fun isSweepRequired(): Boolean {
    val accountStatus = accountService.accountStatus().first().get()
    val activeFullAccount = (accountStatus as? ActiveAccount)?.account as? FullAccount

    return if (activeFullAccount != null) {
      sweepGenerator.generateSweep(activeFullAccount.keybox)
        .map { it.isNotEmpty() }
        .logFailure { "Failure Generating Sweep used to check for funds on old wallets" }
        .get() ?: false
    } else {
      false
    }
  }

  override suspend fun prepareSweep(keybox: Keybox): Result<Sweep?, Error> =
    coroutineBinding {
      val sweepPsbts = sweepGenerator.generateSweep(keybox).bind()

      when {
        sweepPsbts.isEmpty() -> null
        else -> Sweep(unsignedPsbts = sweepPsbts.toSet())
      }
    }
}
