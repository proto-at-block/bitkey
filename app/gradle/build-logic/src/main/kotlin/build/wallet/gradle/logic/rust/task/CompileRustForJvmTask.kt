package build.wallet.gradle.logic.rust.task

import org.gradle.api.file.FileSystemOperations
import javax.inject.Inject

internal abstract class CompileRustForJvmTask : BaseCompileRustTask() {
  @get:Inject
  protected abstract val fileSystemOperations: FileSystemOperations

  override fun compile() {
    rust.get().getToolchain(workdir).cargo {
      commonBuildArgs()

      if (!target.get().isHost) {
        args("--target", target.get().rustTargetName)
      }
    }
  }

  override fun copyBinariesToOutputDirectory() {
    fileSystemOperations.copy {
      val libraryFile =
        targetDirectory.get()
          .let {
            if (target.get().isHost) {
              it
            } else {
              it.dir(target.get().rustTargetName)
            }
          }
          .dir(profile.get().outputDirectoryName)
          .file(getLibraryFileName(libraryName.get(), target.get()))

      from(libraryFile)
      into(outputDirectory)
    }
  }
}
