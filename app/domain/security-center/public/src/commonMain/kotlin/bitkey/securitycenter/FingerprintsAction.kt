package bitkey.securitycenter

import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.home.GettingStartedTask

class FingerprintsAction(
  private val gettingStartedTasks: List<GettingStartedTask>,
  private val fingerprintCount: Int,
  private val firmwareDeviceInfo: FirmwareDeviceInfo?,
  private val fingerprintResetReady: Boolean,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    val recommendations = mutableListOf<SecurityActionRecommendation>()

    if (fingerprintResetReady) {
      recommendations.add(SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET)
    }

    if (fingerprintCount == 1) {
      recommendations.add(SecurityActionRecommendation.ADD_FINGERPRINTS)
    } else if (fingerprintCount > 1) {
      // No additional fingerprints needed
    } else {
      // if fingerprintCount is 0, the user enrolled fingerprints before we started tracking unlock info
      // fallback to the GettingStartedTasks to determine if the user has added additional fingerprints

      // if the AddAdditionalFingerprint task is incomplete, we know that the user has only one fingerprint
      // if the task is not found, a recovery has been completed and the user may have more than one fingerprint
      // if the task is complete, the user at some point added another fingerprint and may still have more than one fingerprint
      val pendingFingerprintsTask = gettingStartedTasks.any {
        it.id == GettingStartedTask.TaskId.AddAdditionalFingerprint &&
          it.state == GettingStartedTask.TaskState.Incomplete
      }

      if (pendingFingerprintsTask || firmwareDeviceInfo == null) {
        recommendations.add(SecurityActionRecommendation.ADD_FINGERPRINTS)
      }
    }

    return recommendations
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.SECURITY
  }

  override fun type(): SecurityActionType = SecurityActionType.FINGERPRINTS

  override fun state(): SecurityActionState {
    return if (getRecommendations().isNotEmpty()) {
      SecurityActionState.HasRecommendationActions
    } else {
      SecurityActionState.Secure
    }
  }
}
