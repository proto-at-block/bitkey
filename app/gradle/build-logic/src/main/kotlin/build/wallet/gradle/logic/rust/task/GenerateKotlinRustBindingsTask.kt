package build.wallet.gradle.logic.rust.task

import build.wallet.gradle.logic.rust.util.RustToolchainProvider
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.LogLevel
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
internal abstract class GenerateKotlinRustBindingsTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val rustLibraryWithDebugSymbols: RegularFileProperty

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @get:Internal
  protected val workdir: Directory = project.layout.projectDirectory

  @Suppress("UnstableApiUsage")
  @get:ServiceReference(RustToolchainProvider.SERVICE)
  protected abstract val rust: Property<RustToolchainProvider>

  init {
    group = "Rust"
    description = "Generate Kotlin UniFFI bindings"
  }

  @TaskAction
  fun runTask() {
    val output = outputDirectory.get().asFile

    output.mkdirs()

    rust.get().getToolchain(workdir).cargo {
      args("run")

      if (logger.isEnabled(LogLevel.INFO)) {
        args("--verbose")
      }

      args("--bin", "uniffi-bindgen")

      args("generate")

      args("--library", rustLibraryWithDebugSymbols.get().asFile.absolutePath)

      args("--language", "kotlin")

      args("--out-dir", output.absolutePath)
    }
  }
}
