package build.wallet.emergencyexitkit

class EmergencyExitKitDataProviderFake(
  var eekAssociation: EmergencyExitKitAssociation = EekDataFake,
) : EmergencyExitKitDataProvider {
  override fun getAssociatedEekData(): EmergencyExitKitAssociation {
    return eekAssociation
  }

  fun reset() {
    eekAssociation = EekDataFake
  }
}
