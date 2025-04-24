package bitkey.securitycenter

import build.wallet.fwup.FirmwareData

class HardwareDeviceAction(
  private val firmwareData: FirmwareData,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    if (firmwareData.firmwareDeviceInfo == null) {
      return listOf(
        SecurityActionRecommendation.PAIR_HARDWARE_DEVICE
      )
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
}
