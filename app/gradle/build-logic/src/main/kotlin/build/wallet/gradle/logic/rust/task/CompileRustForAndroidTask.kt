package build.wallet.gradle.logic.rust.task

import build.wallet.gradle.logic.rust.util.CpuArchitecture
import build.wallet.gradle.logic.rust.util.OsFamily
import build.wallet.gradle.logic.rust.util.RustCompilationProfile
import build.wallet.gradle.logic.rust.util.RustTarget
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import java.io.File
import javax.inject.Inject

internal abstract class CompileRustForAndroidTask : BaseCompileRustTask() {
  @get:Input
  abstract val apiLevel: Property<Int>

  @get:InputDirectory
  abstract val ndkDirectory: Property<File>

  @get:Inject
  protected abstract val fileSystemOperations: FileSystemOperations

  private val clangPath: String
    get() =
      ndkDirectory.get()
        .resolve("toolchains/llvm/prebuilt")
        .resolve(hostArchitecturePrebuiltToolchainName)
        .resolve("bin")
        .resolve(clangTargetTriple + apiLevel.get() + "-clang" + clangFileExtension)
        .absolutePath

  private val hostArchitecturePrebuiltToolchainName: String
    get() =
      when {
        OsFamily.host.isDarwin -> "darwin-x86_64"
        OsFamily.host.isLinux && CpuArchitecture.host.isX64 -> "linux-x86_64"
        OsFamily.host.isWindows && CpuArchitecture.host.isX64 -> "windows-x86_64"
        else -> error("Unsupported host OS - cannot resolve which NDK toolchain to use.")
      }

  private val clangTargetTriple: String
    get() =
      when (val target = target.get()) {
        RustTarget.AndroidArm32 -> "armv7a-linux-androideabi"
        RustTarget.AndroidArm64 -> "aarch64-linux-android"
        RustTarget.AndroidX64 -> "x86_64-linux-android"
        else -> error("Unsupported target: $target.")
      }

  private val clangFileExtension: String
    get() = if (OsFamily.host.isWindows) ".cmd" else ""

  private fun installNdk() {
    rust.get().getToolchain(workdir.get()).installCargoNdk()
  }

  override fun compile() {
    installNdk()

    rust.get().getToolchain(workdir.get()).cargo {
      args("ndk")

      args("--target", target.get().rustTargetName)

      args("--output-dir", outputDirectory.get().asFile.absolutePath)

      args("--platform", apiLevel.get())

      if (profile.get() == RustCompilationProfile.Debug) {
        args("--no-strip")
      }

      commonBuildArgs()

      environment("ANDROID_NDK_ROOT", ndkDirectory.get().absolutePath)
      environment("ANDROID_NDK_HOME", ndkDirectory.get().absolutePath)
      environment("CLANG_PATH", clangPath)
    }
  }

  override fun copyBinariesToOutputDirectory() {
    applyCargoNdkWorkaround()
  }

  private fun applyCargoNdkWorkaround() {
    // TODO(W-9887): once cargo-ndk 3.5.5+ crate version is published:
    //               - bump the version in RustToolchain.kt
    //               - remove this workaround - copyBinariesToOutputDirectory() should be noop.
    //               - delete unused variables.
    //               See https://github.com/bbqsrc/cargo-ndk/issues/143#issuecomment-2423024497.
    // Due to https://github.com/bbqsrc/cargo-ndk/issues/89, cargo-ndk copies
    // output from all existing library targets to the output directory. We
    // then need to delete everything that wasn't built in this task.
    // See https://linear.app/squareup/issue/W-8318 for more context.
    val libraryFile = getLibraryFileName(libraryName.get(), target.get())
    val toDelete = outputDirectory.get().asFileTree.matching {
      exclude {
        it.name == libraryFile
      }
    }
    fileSystemOperations.delete { delete(toDelete) }
  }

  companion object {
    fun getOutputLibraryDirectory(target: RustTarget): String =
      when (target) {
        RustTarget.AndroidArm32 -> "armeabi-v7a"
        RustTarget.AndroidArm64 -> "arm64-v8a"
        RustTarget.AndroidX64 -> "x86_64"
        else -> error("Unsupported target: $target.")
      }
  }
}
