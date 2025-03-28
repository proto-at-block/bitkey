package bitkey.securitycenter

import bitkey.firmware.HardwareUnlockInfoService
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.UnlockMethod
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import com.github.michaelbull.result.getOr

class FingerprintsAction(
  private val gettingStartedTasks: List<GettingStartedTask>,
  private val fingerprintCount: Int,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    if (fingerprintCount == 1) {
      return listOf(SecurityActionRecommendation.ADD_FINGERPRINTS)
    } else if (fingerprintCount > 1) {
      return emptyList()
    }

    // if fingerprintCount is 0, the user enrolled fingerprints before we started tracking unlock info
    // fallback to the GettingStartedTasks to determine if the user has added additional fingerprints

    // if the AddAdditionalFingerprint task is incomplete, we know that the user has only one fingerprint
    // if the task is not found, a recovery has been completed and the user may have more than one fingerprint
    // if the task is complete, the user at some point added another fingerprint and may still have more than one fingerprint
    val pendingFingerprintsTask = gettingStartedTasks.any {
      it.id == GettingStartedTask.TaskId.AddAdditionalFingerprint &&
        it.state == GettingStartedTask.TaskState.Incomplete
    }

    return if (pendingFingerprintsTask) {
      listOf(SecurityActionRecommendation.ADD_FINGERPRINTS)
    } else {
      emptyList()
    }
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.SECURITY
  }
}

interface FingerprintsActionFactory {
  suspend fun create(): SecurityAction?
}

@BitkeyInject(AppScope::class)
class FingerprintsActionFactoryImpl(
  private val gettingStartedTaskDao: GettingStartedTaskDao,
  private val hardwareUnlockInfoService: HardwareUnlockInfoService,
) : FingerprintsActionFactory {
  override suspend fun create(): SecurityAction? {
    val gettingStartedTasks = gettingStartedTaskDao.getTasks()
    val fingerprintCount = hardwareUnlockInfoService.countUnlockInfo(unlockMethod = UnlockMethod.BIOMETRICS).getOr(0)
    return FingerprintsAction(gettingStartedTasks, fingerprintCount)
  }
}
