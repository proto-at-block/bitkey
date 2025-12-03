package bitkey.securitycenter

import build.wallet.firmware.FirmwareDeviceInfo

class FingerprintsAction(
  private val fingerprintCount: Int,
  private val firmwareDeviceInfo: FirmwareDeviceInfo?,
  private val fingerprintResetReady: Boolean,
  private val isAppKeyProvisioned: Boolean,
  private val isFingerprintResetEnabled: Boolean,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    val recommendations = buildList {
      if (fingerprintResetReady) {
        add(SecurityActionRecommendation.COMPLETE_FINGERPRINT_RESET)
      }

      if (!isAppKeyProvisioned && isFingerprintResetEnabled) {
        add(SecurityActionRecommendation.PROVISION_APP_KEY_TO_HARDWARE)
      }

      if (fingerprintCount <= 1 || firmwareDeviceInfo == null) {
        add(SecurityActionRecommendation.ADD_FINGERPRINTS)
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
