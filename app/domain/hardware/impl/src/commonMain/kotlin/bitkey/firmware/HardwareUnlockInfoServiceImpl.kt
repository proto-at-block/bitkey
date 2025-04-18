package bitkey.firmware

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import build.wallet.logging.logFailure
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@BitkeyInject(AppScope::class)
class HardwareUnlockInfoServiceImpl(
  private val dao: HardwareUnlockInfoDao,
  private val clock: Clock,
  private val appCoroutineScope: CoroutineScope,
) : HardwareUnlockInfoService {
  private val unlockInfoList = MutableStateFlow<List<UnlockInfo>>(emptyList())

  init {
    appCoroutineScope.launch {
      dao.getAllUnlockInfo()
        .logFailure { "Failed to get unlock info" }
        .onSuccess {
          unlockInfoList.value = it
        }
    }
  }

  override suspend fun replaceAllUnlockInfo(unlockInfoList: List<UnlockInfo>) {
    dao.replaceAllUnlockInfo(
      unlockInfoList = unlockInfoList,
      createdAt = clock.now()
    )
      .logFailure { "Failed to set unlock info" }
      .onSuccess {
        this.unlockInfoList.value = unlockInfoList
      }
  }

  override suspend fun countUnlockInfo(unlockMethod: UnlockMethod): Flow<Int> {
    return unlockInfoList.map {
      it.count { unlockInfo -> unlockInfo.unlockMethod == unlockMethod }
    }
  }

  override suspend fun clear() {
    dao.clear()
  }
}
