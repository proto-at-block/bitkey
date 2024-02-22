package build.wallet.gradle.dependencylocking

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

internal abstract class DeleteAllDependencyLocksTask : DefaultTask() {
  @get:OutputDirectory
  abstract val allDependencyLocksDirectory: DirectoryProperty

  init {
    description = "Deletes all Gradle dependency lock files in the project. " +
      "This task is used for cleaning up left over lock files after some module was renamed or deleted."
  }

  @TaskAction
  fun runTask() {
    allDependencyLocksDirectory.get().asFile.deleteRecursively()
  }
}
