package build.wallet.gradle.logic.rust.util

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import javax.inject.Inject

internal abstract class RustupService : BuildService<RustupService.Parameters> {
  interface Parameters : BuildServiceParameters {
    val rustupPath: RegularFileProperty
  }

  companion object {
    const val SERVICE = "rustup"
  }

  @get:Inject
  protected abstract val execOperations: ExecOperations

  fun rustup(configure: ExecSpec.() -> Unit) {
    execOperations.exec {
      commandLine(parameters.rustupPath.get())

      configure()
    }
  }
}
