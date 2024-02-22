package build.wallet.gradle.dependencylocking

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.relativeTo

internal abstract class SyncDependencyLockSymLinksTask : DefaultTask() {
  @get:Internal
  abstract val projectDependencyLockDirectory: DirectoryProperty

  @get:Internal
  abstract val projectSymLinkDirectory: DirectoryProperty

  init {
    description = "Syncs symbolic links for lock files belonging to the given module. " +
      "These symbolic links point to the actual lock files which are stored outside of the module."
    this.doNotTrackState(
      "This task is very fast, and therefore, does not significantly benefit from incremental compilation."
    )
  }

  @TaskAction
  fun runTask() {
    val projectSymLinkDirectoryFile = projectSymLinkDirectory.get().asFile

    val existingSymLinks = projectSymLinkDirectoryFile.listAllLockFiles()
    val existingLockFiles = projectDependencyLockDirectory.get().asFile.listAllLockFiles()

    existingSymLinks.forEach {
      it.delete()
    }

    existingLockFiles.forEach { lockFile ->
      val existingLockFilePath = lockFile.toPath()

      val linkPath = projectSymLinkDirectoryFile.resolve(lockFile.name).toPath()

      val symLinkPath = existingLockFilePath.relativeTo(projectSymLinkDirectoryFile.toPath())
      linkPath.createSymbolicLinkPointingTo(symLinkPath)
    }
  }

  private fun File.listAllLockFiles(): List<File> =
    listFiles()?.filter { it.name.endsWith("gradle.lockfile") } ?: emptyList()
}
