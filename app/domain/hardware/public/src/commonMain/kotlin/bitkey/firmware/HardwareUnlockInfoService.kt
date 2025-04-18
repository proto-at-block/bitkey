package bitkey.firmware

import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import kotlinx.coroutines.flow.Flow

interface HardwareUnlockInfoService {
  /** Replace all [UnlockInfo] */
  suspend fun replaceAllUnlockInfo(unlockInfoList: List<UnlockInfo>)

  /** Count of [UnlockInfo] with type of [UnlockMethod] . */
  suspend fun countUnlockInfo(unlockMethod: UnlockMethod): Flow<Int>

  /** Clear all [UnlockInfo] */
  suspend fun clear()
}
