package build.wallet.gradle.logic.reproducible

internal class ReproducibleBuildVariables(
  val data: MutableMap<String, String> = mutableMapOf(),
) {
  var bugsnagId: String by data
}
