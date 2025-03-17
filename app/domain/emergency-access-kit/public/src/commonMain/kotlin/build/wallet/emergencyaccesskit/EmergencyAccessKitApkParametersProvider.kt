package build.wallet.emergencyaccesskit

interface EmergencyAccessKitApkParametersProvider {
  fun parameters(): ApkParameters
}

data class ApkParameters(
  /**
   * The version of the APK, displayed in Step 1. Eg. "2024.1.0".
   */
  val apkVersion: String,
  /**
   * The link text to be displayed in Step 1. Eg. "Tap to download".
   */
  val apkLinkText: String,
  /**
   * A URL where the APK file is hosted, displayed in Step 1. Eg.
   * "https://raw.githubusercontent.com/githubtraining/hellogitworld/master/README.txt".
   */
  val apkLinkUrl: String,
  /**
   * The QR code text used to generate the QR code of the APK download link in Step 1.
   */
  val apkLinkQRCodeText: String,
  /**
   * An APK hash string displayed in Step 2, eg.
   * "05b76808d5693855b70556ed13b1f89ebc3649a10cb16a3cae9cff183ff3bf8a".
   */
  val apkHash: String,
)
