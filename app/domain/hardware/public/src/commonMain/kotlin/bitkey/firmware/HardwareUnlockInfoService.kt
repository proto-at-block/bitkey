package bitkey.firmware

import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import com.github.michaelbull.result.Result

interface HardwareUnlockInfoService {
  /** Replace all [UnlockInfo] */
  suspend fun replaceAllUnlockInfo(unlockInfoList: List<UnlockInfo>)

  /** Count of [UnlockInfo] with type of [UnlockMethod] . */
  suspend fun countUnlockInfo(unlockMethod: UnlockMethod): Result<Int, Error>

  /** Clear all [UnlockInfo] */
  suspend fun clear()
}
