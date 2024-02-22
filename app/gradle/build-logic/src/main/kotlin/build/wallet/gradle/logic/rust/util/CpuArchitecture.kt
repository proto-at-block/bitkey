package build.wallet.gradle.logic.rust.util

import org.apache.tools.ant.taskdefs.condition.Os

internal enum class CpuArchitecture {
  X64,
  Arm64,
  ;

  val isX64: Boolean
    get() = this == X64

  val isArm64: Boolean
    get() = this == Arm64

  companion object {
    val host: CpuArchitecture by lazy {
      when {
        Os.isArch("aarch64") -> Arm64
        Os.isArch("amd64") || Os.isArch("x86_64") -> X64
        else -> error("Unknown host CPU architecture.")
      }
    }
  }
}
