package build.wallet.gradle.logic.reproducible

class ReproducibleBuildVariables(
  val data: MutableMap<String, String> = mutableMapOf(),
) {
  var bugsnagId: String by data
  var emergencyApkHash: String by data
  var emergencyApkVersion: String by data
  var emergencyApkUrl: String by data
}
