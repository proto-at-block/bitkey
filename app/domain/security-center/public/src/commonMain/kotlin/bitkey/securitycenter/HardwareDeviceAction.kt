package bitkey.securitycenter

import build.wallet.fwup.FirmwareData

class HardwareDeviceAction(
  private val firmwareData: FirmwareData,
  private val isHardwareDescriptorLoaded: Boolean = true,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    if (firmwareData.firmwareDeviceInfo == null) {
      return listOf(
        SecurityActionRecommendation.PAIR_HARDWARE_DEVICE
      )
    }

    if (!isHardwareDescriptorLoaded) {
      return listOf(SecurityActionRecommendation.PROVISION_HARDWARE_DESCRIPTOR)
    }

    return when (firmwareData.firmwareUpdateState) {
      is FirmwareData.FirmwareUpdateState.UpToDate -> emptyList()
      is FirmwareData.FirmwareUpdateState.PendingUpdate -> listOf(
        SecurityActionRecommendation.UPDATE_FIRMWARE
      )
    }
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.SECURITY
  }

  override fun type(): SecurityActionType = SecurityActionType.HARDWARE_DEVICE

  override fun state(): SecurityActionState {
    return when {
      getRecommendations().any {
        it == SecurityActionRecommendation.PAIR_HARDWARE_DEVICE ||
          it == SecurityActionRecommendation.PROVISION_HARDWARE_DESCRIPTOR
      } -> SecurityActionState.HasCriticalActions
      getRecommendations().isNotEmpty() -> SecurityActionState.HasRecommendationActions
      else -> SecurityActionState.Secure
    }
  }
}
