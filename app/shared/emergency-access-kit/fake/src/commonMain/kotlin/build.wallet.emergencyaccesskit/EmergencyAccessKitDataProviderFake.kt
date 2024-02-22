package build.wallet.emergencyaccesskit

class EmergencyAccessKitDataProviderFake(
  private val eakAssociation: EmergencyAccessKitAssociation,
) : EmergencyAccessKitDataProvider {
  override fun getAssociatedEakData(): EmergencyAccessKitAssociation {
    return eakAssociation
  }
}
