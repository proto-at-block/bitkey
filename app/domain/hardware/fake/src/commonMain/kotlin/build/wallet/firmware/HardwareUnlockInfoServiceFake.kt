package build.wallet.firmware

import bitkey.firmware.HardwareUnlockInfoService
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class HardwareUnlockInfoServiceFake : HardwareUnlockInfoService {
  private var unlockInfo = emptyList<UnlockInfo>()

  override suspend fun replaceAllUnlockInfo(unlockInfoList: List<UnlockInfo>) {
    unlockInfo = unlockInfoList
  }

  override suspend fun countUnlockInfo(unlockMethod: UnlockMethod): Result<Int, Error> {
    return Ok(unlockInfo.count { it.unlockMethod == unlockMethod })
  }

  override suspend fun clear() {
    unlockInfo = emptyList()
  }
}
