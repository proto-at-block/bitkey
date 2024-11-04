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
      // TODO(W-9887): once cargo-ndk 3.5.5+ is published, bump this version and
      //               remove workaround from CompileRustForAndroidTask.kt.
      args("install", "cargo-ndk@3.5.4")
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
