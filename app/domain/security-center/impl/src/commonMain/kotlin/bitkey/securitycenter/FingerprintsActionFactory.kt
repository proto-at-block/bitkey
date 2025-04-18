package bitkey.securitycenter

import bitkey.firmware.HardwareUnlockInfoService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.UnlockMethod
import build.wallet.home.GettingStartedTaskDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface FingerprintsActionFactory {
  suspend fun create(): Flow<SecurityAction?>
}

@BitkeyInject(AppScope::class)
class FingerprintsActionFactoryImpl(
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
) : FingerprintsActionFactory {
  override suspend fun create(): Flow<SecurityAction> {
    val gettingStartedTasks = gettingStartedTaskDao.getTasks()
    return hardwareUnlockInfoService.countUnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS).map {
      FingerprintsAction(gettingStartedTasks, it)
    }
  }
}
