package build.wallet.gradle.logic.rust.util

import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.io.File

internal class RustToolchain(
  private val rootProjectExecOperations: ExecOperations,
  private val rustupPath: File,
  private val cargoPath: File,
  private val workdir: File,
) {
  private val lock = Any()

  init {
    rustup {
      args("show", "active-toolchain")
    }
  }

  fun addTarget(target: RustTarget) {
    rustup {
      args("target", "add", target.rustTargetName)
    }
  }

  fun installCargoNdk() {
    cargo {
      args("install", "cargo-ndk")
    }
  }

  fun cargo(configure: ExecSpec.() -> Unit) {
    synchronizedWithInternalLock {
      rootProjectExecOperations.exec {
        workingDir(workdir)
        commandLine(cargoPath)
        configure()
      }
    }
  }

  private fun rustup(configure: ExecSpec.() -> Unit) {
    synchronizedWithInternalLock {
      rootProjectExecOperations.exec {
        workingDir(workdir)
        commandLine(rustupPath)

        configure()
      }
    }
  }

  private inline fun <T> synchronizedWithInternalLock(perform: () -> T): T {
    return synchronized(lock, perform)
  }
}
