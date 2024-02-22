package build.wallet.emergencyaccesskit

class EmergencyAccessKitApkParametersProviderImpl(
  private val emergencyAccessKitDataProvider: EmergencyAccessKitDataProvider,
) : EmergencyAccessKitApkParametersProvider {
  override fun parameters(): ApkParameters =
    when (val eakData = emergencyAccessKitDataProvider.getAssociatedEakData()) {
      is EmergencyAccessKitAssociation.AssociatedData ->
        ApkParameters(
          apkVersion = eakData.version,
          apkLinkText = eakData.url.withoutHttpPrefix(),
          apkLinkUrl = eakData.url,
          apkLinkQRCodeText = eakData.url,
          apkHash = eakData.hash
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
