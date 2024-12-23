package build.wallet.emergencyaccesskit

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.AppVariant

@BitkeyInject(AppScope::class)
class EmergencyAccessKitDataProviderImpl(
  private val appVariant: AppVariant,
) : EmergencyAccessKitDataProvider {
  override fun getAssociatedEakData(): EmergencyAccessKitAssociation {
    return when (appVariant) {
      AppVariant.Emergency -> EmergencyAccessKitAssociation.EakBuild
      AppVariant.Development ->
        EmergencyAccessKitAssociation.AssociatedData(
          hash = "05b76808d5693855b70556ed13b1f89ebc3649a10cb16a3cae9cff183ff3bf8a",
          url = "https://www.loremipsum.dolor/sit-amet-ut-quia-quaerat-sed",
          version = "1234567890"
        )
      AppVariant.Team, AppVariant.Customer, AppVariant.Beta ->
        EmergencyAccessKitAssociation.AssociatedData(
          hash = EmergencyAccessKitAppInformation.ApkHash,
          url = EmergencyAccessKitAppInformation.ApkUrl,
          version = EmergencyAccessKitAppInformation.ApkVersion
        ).takeIf { it.hash.isNotBlank() && it.url.isNotBlank() && it.version.isNotBlank() }
          ?: EmergencyAccessKitAssociation.Unavailable
    }
  }
}
