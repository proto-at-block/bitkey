package build.wallet.emergencyaccesskit

class EmergencyAccessKitDataProviderFake(
  var eakAssociation: EmergencyAccessKitAssociation = EakDataFake,
) : EmergencyAccessKitDataProvider {
  override fun getAssociatedEakData(): EmergencyAccessKitAssociation {
    return eakAssociation
  }

  fun reset() {
    eakAssociation = EakDataFake
  }
}
