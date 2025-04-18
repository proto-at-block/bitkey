package build.wallet.firmware

import bitkey.firmware.HardwareUnlockInfoService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class HardwareUnlockInfoServiceFake : HardwareUnlockInfoService {
  private var unlockInfo: MutableStateFlow<List<UnlockInfo>> = MutableStateFlow(emptyList())

  override suspend fun replaceAllUnlockInfo(unlockInfoList: List<UnlockInfo>) {
    unlockInfo.value = unlockInfoList
  }

  override suspend fun countUnlockInfo(unlockMethod: UnlockMethod): Flow<Int> {
    return unlockInfo.map {
      it.count { it.unlockMethod == unlockMethod }
    }
  }

  override suspend fun clear() {
    unlockInfo.value = emptyList()
  }
}
