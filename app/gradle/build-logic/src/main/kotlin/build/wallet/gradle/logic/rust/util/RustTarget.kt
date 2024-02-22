package build.wallet.gradle.logic.rust.util

internal enum class RustTarget(
  val rustTargetName: String,
  val libraryExtension: String,
) {
  LinuxX64("x86_64-unknown-linux-gnu", "so"),
  DarwinArm64("aarch64-apple-darwin", "dylib"),
  AndroidArm32("armv7-linux-androideabi", "so"),
  AndroidArm64("aarch64-linux-android", "so"),
  AndroidX64("x86_64-linux-android", "so"),
  ;

  val flavorName: String
    get() = this.name

  val isHost: Boolean
    get() = this == RustTarget.host

  companion object {
    val host: RustTarget? by lazy {
      when {
        OsFamily.host.isDarwin && CpuArchitecture.host.isArm64 -> DarwinArm64
        OsFamily.host.isLinux && CpuArchitecture.host.isX64 -> LinuxX64
        else -> null
      }
    }
  }
}
