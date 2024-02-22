package build.wallet.gradle.logic.rust.util

import org.apache.tools.ant.taskdefs.condition.Os

internal enum class OsFamily {
  Darwin,
  Linux,
  Windows,
  ;

  val isDarwin: Boolean
    get() = this == Darwin

  val isLinux: Boolean
    get() = this == Linux

  val isWindows: Boolean
    get() = this == Windows

  companion object {
    val host: OsFamily by lazy {
      when {
        // Order is important because Darwin is also Unix
        Os.isFamily(Os.FAMILY_MAC) -> Darwin
        Os.isFamily(Os.FAMILY_UNIX) -> Linux
        Os.isFamily(Os.FAMILY_WINDOWS) -> Windows
        else -> error("Unsupported host OS - cannot resolve which NDK toolchain to use.")
      }
    }
  }
}
