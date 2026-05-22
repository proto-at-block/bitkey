package build.wallet.device.wipe

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.db.DbTransactionError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.coroutines.flow.first

/**
 * Locally persisted W3-upgrade history needed to decide whether the previous W1 can be wiped.
 *
 * The upgrade can complete before the old W1 is safe to wipe. This state lets the app remember
 * whether there was no old-W1 sweep, which sweep txids still need confirmations, and whether the
 * customer already dismissed the automatic reminder.
 */
data class W3UpgradeDeviceHistory(
  val oldDeviceSerial: String?,
  val oldHardwareFingerprint: String?,
  val oldW1SweepStatus: OldW1SweepStatus,
  val oldW1WipeReminderDismissed: Boolean,
  val sweepTxids: Set<String>,
)

/**
 * Safety checkpoint for old-W1 funds after a W3 upgrade.
 */
enum class OldW1SweepStatus {
  /**
   * Legacy or incomplete local state. The automatic app-open reminder must not use this status.
   */
  UNKNOWN,

  /**
   * The W3 upgrade confirmed there were no old-W1 funds to sweep.
   */
  NOT_REQUIRED,

  /**
   * The W3 upgrade recorded one or more sweep txids that must reach the confirmation threshold.
   */
  CONFIRMATIONS_REQUIRED,
}

interface W3UpgradeDeviceHistoryRepository {
  suspend fun getDeviceHistory(): Result<W3UpgradeDeviceHistory?, DbError>

  suspend fun setOldW1SweepStatus(status: OldW1SweepStatus): Result<Unit, DbError>

  suspend fun replaceSweepTransactions(txids: Set<String>): Result<Unit, DbError>

  suspend fun markOldW1WipeReminderDismissed(): Result<Unit, DbError>
}

@BitkeyInject(AppScope::class)
class W3UpgradeDeviceHistoryRepositoryImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : W3UpgradeDeviceHistoryRepository {
  override suspend fun getDeviceHistory(): Result<W3UpgradeDeviceHistory?, DbError> = coroutineBinding {
    val database = databaseProvider.database()
    val sweepTxids = database
      .w3UpgradeMigrationQueries
      .getSweepTransactions()
      .awaitAsListResult()
      .bind()
      .map { it.txid }
      .toSet()

    database
      .w3UpgradeMigrationQueries
      .getState()
      .asFlowOfOneOrNull()
      .first()
      .bind()
      ?.let {
        W3UpgradeDeviceHistory(
          oldDeviceSerial = it.oldDeviceSerial,
          oldHardwareFingerprint = it.oldHardwareFingerprint,
          oldW1SweepStatus = OldW1SweepStatus.valueOf(it.oldW1SweepStatus),
          oldW1WipeReminderDismissed = it.oldW1WipeReminderDismissed,
          sweepTxids = sweepTxids
        )
      }
  }

  override suspend fun setOldW1SweepStatus(
    status: OldW1SweepStatus,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setOldW1SweepStatus(status.name)
      }
  }

  override suspend fun replaceSweepTransactions(txids: Set<String>): Result<Unit, DbError> {
    if (txids.isEmpty()) {
      return Err(DbTransactionError(Error("Cannot replace W3 upgrade sweep transactions with an empty set")))
    }

    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        clearSweepTransactions()
        txids.forEach { txid ->
          insertSweepTransaction(
            txid = txid,
            broadcastTime = null
          )
        }
        setOldW1SweepStatus(OldW1SweepStatus.CONFIRMATIONS_REQUIRED.name)
      }
  }

  override suspend fun markOldW1WipeReminderDismissed(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setOldW1WipeReminderDismissed()
      }
  }
}
