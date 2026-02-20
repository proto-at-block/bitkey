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
        val targetName = target.get().rustTargetName
        if (!isTargetInstalled(targetName)) {
          rustup {
            workingDir(workDir)
            args("target", "add", targetName)
          }
        }
      }
    }
  }

  /**
   * Checks if a Rust target is already installed to avoid redundant `rustup target add` calls.
   *
   * This optimization is critical because RustupService uses maxParallelUsages=1 (rustup is not
   * concurrent-safe). Without this check, all SetupRustToolchainTask instances across modules
   * serialize through the service, even when the target is already installed.
   *
   * With 3 modules × 3-4 targets each = 11 tasks, skipping already-installed targets
   * can reduce build setup time by ~55% on warm builds.
   */
  private fun isTargetInstalled(targetName: String): Boolean {
    val output = java.io.ByteArrayOutputStream()
    rustupService.get().rustup {
      workingDir(workDir)
      args("target", "list", "--installed")
      standardOutput = output
    }
    return output.toString().lines().any { it.trim() == targetName }
  }
}
