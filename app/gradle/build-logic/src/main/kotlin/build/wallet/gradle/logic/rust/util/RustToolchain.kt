package build.wallet.gradle.logic.rust.util

import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.File

internal class RustToolchain(
  private val rootProjectExecOperations: ExecOperations,
  private val cargoPath: File,
  private val workdir: File,
) {
  fun installCargoNdk() {
    cargo {
      args("install", "cargo-ndk")
    }
  }

  fun cargo(configure: ExecSpec.() -> Unit) {
    rootProjectExecOperations.exec {
      workingDir(workdir)
      commandLine(cargoPath)
      configure()
    }
  }
}
