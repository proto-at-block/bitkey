package build.wallet.gradle.dependencylocking.task

import build.wallet.gradle.dependencylocking.exception.LockFileUnificationException
import build.wallet.gradle.dependencylocking.lockfile.LockFile
import build.wallet.gradle.dependencylocking.lockfile.LockFileUnificationResult
import build.wallet.gradle.dependencylocking.lockfile.deserialize
import build.wallet.gradle.dependencylocking.lockfile.serializeTo
import build.wallet.gradle.dependencylocking.lockfile.unifyWith
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

internal abstract class UpdateDependencyLockFileTask : DefaultTask() {
  @get:InputFiles
  abstract val localLockFileSegments: Property<FileCollection>

  @get:OutputDirectory
  abstract val globalLockFileDirectory: DirectoryProperty

  @get:Internal
  abstract val rootProjectDirectory: DirectoryProperty

  init {
    description = "Updates Gradle lock files in the given module."
    group = "dependency-lock"

    this.doNotTrackState(
      "It is not possible to dynamically set multiple directories as input."
    )
  }

  @TaskAction
  fun runTask() {
    val unifiedLockFile = createUnifiedLockFile()

    val lockFileDirectory = globalLockFileDirectory.get().asFile

    unifiedLockFile.serializeTo(lockFileDirectory)
  }

  private fun createUnifiedLockFile(): LockFile =
    localLockFileSegments.get()
      .map { LockFile.LockedModule.deserialize(it) to it }
      .map { LockFile(listOf(it.first)) to it.second }
      .fold(LockFile()) { acc, (lockFile, lockFileOrigin) ->
        acc.unifyWith(lockFile).unwrap(lockFileOrigin)
      }

  private fun LockFileUnificationResult<LockFile>.unwrap(lockFileOrigin: File): LockFile =
    when (this) {
      is LockFileUnificationResult.Success -> value
      is LockFileUnificationResult.Error -> {
        throw LockFileUnificationException(
          this,
          "other modules",
          lockFileOrigin.toRelativeString(rootProjectDirectory.get().asFile)
        )
      }
    }
}
