package build.wallet.emergencyexitkit

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.AppVariant

@BitkeyInject(AppScope::class)
class EmergencyExitKitDataProviderImpl(
  private val appVariant: AppVariant,
) : EmergencyExitKitDataProvider {
  override fun getAssociatedEekData(): EmergencyExitKitAssociation {
    return when (appVariant) {
      AppVariant.Emergency -> EmergencyExitKitAssociation.EekBuild
      AppVariant.Development ->
        EmergencyExitKitAssociation.AssociatedData(
          hash = "05b76808d5693855b70556ed13b1f89ebc3649a10cb16a3cae9cff183ff3bf8a",
          url = "https://www.loremipsum.dolor/sit-amet-ut-quia-quaerat-sed",
          version = "1234567890"
        )
      AppVariant.Team, AppVariant.Alpha, AppVariant.Customer ->
        EmergencyExitKitAssociation.AssociatedData(
          hash = EmergencyExitKitAppInformation.ApkHash,
          url = EmergencyExitKitAppInformation.ApkUrl,
          version = EmergencyExitKitAppInformation.ApkVersion
        ).takeIf { it.hash.isNotBlank() && it.url.isNotBlank() && it.version.isNotBlank() }
          ?: EmergencyExitKitAssociation.Unavailable
    }
  }
}
