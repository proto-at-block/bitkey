package build.wallet.gradle.logic.rust.util

internal enum class RustCompilationProfile(
  val cargoProfileName: String,
  val outputDirectoryName: String,
) {
  Debug("dev", "debug"),
  Release("release", "release"),
  ;

  val flavorName: String
    get() = this.name
}
