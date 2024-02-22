package build.wallet.gradle.logic.rust.util

import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

internal abstract class RustToolchainProvider : BuildService<RustToolchainProvider.Parameters> {
  interface Parameters : BuildServiceParameters {
    val rustupPath: RegularFileProperty
    val cargoPath: RegularFileProperty
  }

  companion object {
    const val SERVICE = "rust"
  }

  private val toolchains = mutableMapOf<File, RustToolchain>()

  @get:Inject
  protected abstract val execOperations: ExecOperations

  @Synchronized
  fun getToolchain(workdir: Directory): RustToolchain {
    val workdirFile = workdir.asFile
    return toolchains.getOrPut(workdirFile) {
      RustToolchain(
        rootProjectExecOperations = execOperations,
        rustupPath = parameters.rustupPath.get().asFile,
        cargoPath = parameters.cargoPath.get().asFile,
        workdir = workdirFile
      )
    }
  }
}
