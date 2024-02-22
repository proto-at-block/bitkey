package build.wallet.gradle.logic.gradle

/**
 * Describes environment of the compiling host machine.
 *
 * @property archName - host's architecture name.
 * @property isArm64Compatible - determines if host's architecture supports arm64 (eg apple silicon).
 */
data class HostEnvironment(
  val archName: String = hostArchName(),
  val isArm64Compatible: Boolean = archName == "aarch64",
)

private fun hostArchName(): String = System.getProperty("os.arch")
