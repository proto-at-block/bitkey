package bitkey.firmware

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.UnlockInfo
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.datetime.Instant

@BitkeyInject(AppScope::class)
class HardwareUnlockInfoDao(
  private val databaseProvider: BitkeyDatabaseProvider,
) {
  suspend fun getAllUnlockInfo(): Result<List<UnlockInfo>, Error> {
    return databaseProvider.database().hardwareUnlockMethodsQueries
      .selectAll()
      .awaitAsListResult()
      .map { rows ->
        rows.map { row ->
          UnlockInfo(
            unlockMethod = row.unlockMethod,
            fingerprintIdx = row.unlockMethodIdx?.toInt()
          )
        }
      }
  }

  suspend fun replaceAllUnlockInfo(
    unlockInfoList: List<UnlockInfo>,
    createdAt: Instant,
  ): Result<Unit, Error> {
    return databaseProvider.database().awaitTransaction {
      hardwareUnlockMethodsQueries.clearHardwareUnlockMethods()
      unlockInfoList.forEach { unlockInfo ->
        hardwareUnlockMethodsQueries.insertHardwareUnlockMethod(
          unlockMethod = unlockInfo.unlockMethod,
          unlockMethodIdx = unlockInfo.fingerprintIdx?.toLong(),
          createdAt = createdAt
        )
      }
    }
  }

  suspend fun clear(): Result<Unit, Error> {
    return databaseProvider.database().awaitTransaction {
      hardwareUnlockMethodsQueries.clearHardwareUnlockMethods()
    }
  }
}
