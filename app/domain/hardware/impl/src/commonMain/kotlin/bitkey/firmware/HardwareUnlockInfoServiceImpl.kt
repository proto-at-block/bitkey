package bitkey.firmware

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class HardwareUnlockInfoServiceImpl(
  private val dao: HardwareUnlockInfoDao,
  private val clock: Clock,
) : HardwareUnlockInfoService {
  override suspend fun replaceAllUnlockInfo(unlockInfoList: List<UnlockInfo>) {
    dao.replaceAllUnlockInfo(
      unlockInfoList = unlockInfoList,
      createdAt = clock.now()
    ).logFailure { "Failed to set unlock info" }
  }

  override suspend fun countUnlockInfo(unlockMethod: UnlockMethod): Result<Int, Error> {
    return dao.getAllUnlockInfo()
      .logFailure { "Failed to get unlock info" }
      .map { it.count { unlockInfo -> unlockInfo.unlockMethod == unlockMethod } }
  }

  override suspend fun clear() {
    dao.clear()
  }
}
