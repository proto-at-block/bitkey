package build.wallet.gradle.dependencylocking

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import java.io.File

/**
 * Custom plugin to configure Gradle dependency locking.
 */
class DependencyLockingPlugin : Plugin<Project> {
  // Warning: This directory will be deleted by the `DeleteAllDependencyLocksTask` task
  private val Project.allDependencyLocksDirectory: File
    get() =
      rootDir
        .resolve("gradle")
        .resolve("dependency-locks")
        .also { it.mkdirs() }

  private val Project.dependencyLockRelativeParentPath: String
    get() = layout.projectDirectory.asFile.relativeTo(rootDir).path

  private val Project.projectDependencyLockDirectory: File
    get() =
      allDependencyLocksDirectory
        .resolve(dependencyLockRelativeParentPath)
        .also { it.mkdirs() }

  override fun apply(target: Project) {
    with(target) {
      configureBuildDependencyLocking()

      configureBuildScriptDependencyLocking()

      registerUpdateDependencyLockTask()

      registerSyncSymLinksTask()

      if (project == rootProject) {
        registerDeleteDependencyLockFiles()
      }
    }
  }

  private fun Project.configureBuildDependencyLocking() {
  }

  private fun Project.configureBuildScriptDependencyLocking() {
  }

  private fun Project.registerUpdateDependencyLockTask() {
    tasks.register<UpdateDependencyLockTask>("updateDependencyLock") {
    }
  }

  private fun Project.registerSyncSymLinksTask() {
    tasks.register<SyncDependencyLockSymLinksTask>("syncDependencyLockSymLinks") {
      projectDependencyLockDirectory.set(
        this@registerSyncSymLinksTask.projectDependencyLockDirectory
      )
      projectSymLinkDirectory.set(layout.projectDirectory)
    }
  }

  private fun Project.registerDeleteDependencyLockFiles() {
    tasks.register<DeleteAllDependencyLocksTask>("deleteAllDependencyLocks") {
      allDependencyLocksDirectory.set(
        this@registerDeleteDependencyLockFiles.allDependencyLocksDirectory
      )
    }
  }
}
