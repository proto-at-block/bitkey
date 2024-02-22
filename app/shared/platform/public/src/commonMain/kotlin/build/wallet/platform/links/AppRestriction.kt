package build.wallet.platform.links

/**
 * Specifies the [minVersion] and the [packageName] for the App we want to open.
 * These restrictions are only checked for Android. Since we cannot check
 * which version of the app we have in IOS.
 */
data class AppRestrictions(
  val packageName: String,
  val minVersion: Long,
)

/**
 * Interface used to access Android's [PackageInfo]
 */
interface PackageInfo {
  val packageName: String
  val longVersionCode: Long
}
