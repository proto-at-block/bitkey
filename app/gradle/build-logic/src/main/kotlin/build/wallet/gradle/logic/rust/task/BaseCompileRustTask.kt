package build.wallet.gradle.logic.rust.task

import build.wallet.gradle.logic.rust.util.RustCompilationProfile
import build.wallet.gradle.logic.rust.util.RustTarget
import build.wallet.gradle.logic.rust.util.RustToolchainProvider
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecSpec

internal abstract class BaseCompileRustTask : DefaultTask() {
  @get:Input
  abstract val target: Property<RustTarget>

  @get:Input
  abstract val profile: Property<RustCompilationProfile>

  @get:Input
  abstract val packageName: Property<String>

  @get:Input
  abstract val libraryName: Property<String>

  @get:Internal
  abstract val targetDirectory: DirectoryProperty

  @get:Internal
  abstract val workdir: DirectoryProperty

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @Suppress("UnstableApiUsage")
  @get:ServiceReference(RustToolchainProvider.SERVICE)
  protected abstract val rust: Property<RustToolchainProvider>

  init {
    group = "Rust"
    description = "Compile Rust for Kotlin Multiplatform target"
  }

  @TaskAction
  fun runTask() {
    compile()

    copyBinariesToOutputDirectory()
  }

  protected fun ExecSpec.commonBuildArgs() {
    args("build")

    if (logger.isEnabled(LogLevel.INFO)) {
      args("--verbose")
    }

    args("--profile", profile.get().cargoProfileName)

    args("--target-dir", targetDirectory.get().asFile.absolutePath)

    args("--lib")

    args("--package", packageName.get())

    args("--locked")
  }

  protected abstract fun compile()

  protected abstract fun copyBinariesToOutputDirectory()

  companion object {
    fun getLibraryFileName(
      libraryName: String,
      target: RustTarget,
    ): String = "lib" + libraryName + "." + target.libraryExtension
  }
}
