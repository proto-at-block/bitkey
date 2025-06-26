package build.wallet.emergencyexitkit

class EmergencyExitKitApkParametersProviderFake : EmergencyExitKitApkParametersProvider {
  override fun parameters(): ApkParameters =
    ApkParameters(
      apkVersion = "1234567890",
      apkLinkText = "www.loremipsum.dolor/sit-amet-ut-quia-quaerat-sed",
      apkLinkUrl = "https://www.loremipsum.dolor/sit-amet-ut-quia-quaerat-sed",
      apkLinkQRCodeText = "https://www.loremipsum.dolor/sit-amet-ut-quia-quaerat-sed",
      apkHash = "05b76808d5693855b70556ed13b1f89ebc3649a10cb16a3cae9cff183ff3bf8a"
    )
}
