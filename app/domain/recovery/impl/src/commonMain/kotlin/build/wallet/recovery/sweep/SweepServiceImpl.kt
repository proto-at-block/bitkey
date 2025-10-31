package build.wallet.recovery.sweep

import build.wallet.account.AccountService
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.activity.TransactionActivityOperations
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.keybox.Keybox
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.sweep.SweepService.SweepError.NoFundsToSweep
import build.wallet.recovery.sweep.SweepService.SweepError.SweepGenerationFailed
import build.wallet.worker.RefreshOperationFilter
import build.wallet.worker.RunStrategy
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class SweepServiceImpl(
  private val accountService: AccountService,
  private val sweepGenerator: SweepGenerator,
  sweepSyncFrequency: SweepSyncFrequency,
  private val uuidGenerator: UuidGenerator,
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

  override fun markSweepHandled() {
    sweepRequired.value = false
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

  override suspend fun estimateSweepWithMockDestination(
    keybox: Keybox,
  ): Result<Sweep, SweepService.SweepError> =
    coroutineBinding {
      // Create a fake destination keyset to trick the sweep generator into treating
      // the currently active keyset as a source that must be swept.
      val fakeDestinationKeyset = keybox.activeSpendingKeyset.copy(
        f8eSpendingKeyset = keybox.activeSpendingKeyset.f8eSpendingKeyset.copy(
          keysetId = uuidGenerator.random()
        )
      )

      val mockKeybox = keybox.copy(
        activeSpendingKeyset = fakeDestinationKeyset,
        keysets = keybox.keysets + fakeDestinationKeyset
      )

      val sweep = prepareSweep(mockKeybox)
        .mapError { SweepGenerationFailed(it) }
        .bind()

      // If sweep is null, there are no funds to sweep (either zero balance or fees exceed balance)
      sweep ?: Err(NoFundsToSweep).bind()
    }
}
