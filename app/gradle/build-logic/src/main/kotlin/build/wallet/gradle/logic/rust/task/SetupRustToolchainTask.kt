package build.wallet.gradle.logic.rust.task

import build.wallet.gradle.logic.rust.util.RustTarget
import build.wallet.gradle.logic.rust.util.RustupService
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

internal abstract class SetupRustToolchainTask : DefaultTask() {
  @get:Input
  abstract val target: Property<RustTarget>

  @get:Internal
  protected val workDir: Directory = project.layout.projectDirectory

  @Suppress("UnstableApiUsage")
  @get:ServiceReference(RustupService.SERVICE)
  protected abstract val rustupService: Property<RustupService>

  init {
    group = "Rust"
    description = "Install the Rust toolchain"
  }

  @TaskAction
  fun setupToolchain() {
    rustupService.get().apply {
      rustup {
        workingDir(workDir)
        args("show", "active-toolchain")
      }
      if (!target.get().isHost) {
        rustup {
          workingDir(workDir)
          args("target", "add", target.get().rustTargetName)
        }
      }
    }
  }
}
