package build.wallet.emergencyexitkit

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class EmergencyExitKitApkParametersProviderImpl(
  private val emergencyExitKitDataProvider: EmergencyExitKitDataProvider,
) : EmergencyExitKitApkParametersProvider {
  override fun parameters(): ApkParameters =
    when (val eekData = emergencyExitKitDataProvider.getAssociatedEekData()) {
      is EmergencyExitKitAssociation.AssociatedData ->
        ApkParameters(
          apkVersion = eekData.version,
          apkLinkText = eekData.url.withoutHttpPrefix(),
          apkLinkUrl = eekData.url,
          apkLinkQRCodeText = eekData.url,
          apkHash = eekData.hash
        )
      else ->
        ApkParameters(
          apkVersion = "",
          apkLinkText = "",
          apkLinkUrl = "",
          apkLinkQRCodeText = "",
          apkHash = ""
        )
    }

  /**
   * The design calls for the link text to be the link without the https:// or http:// prefix.
   */
  private fun String.withoutHttpPrefix(): String = removePrefix("https://").removePrefix("http://")
}
