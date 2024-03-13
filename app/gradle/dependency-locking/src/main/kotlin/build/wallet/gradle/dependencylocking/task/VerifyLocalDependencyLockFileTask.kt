package build.wallet.gradle.dependencylocking.task

import build.wallet.gradle.dependencylocking.extension.dependencyLockingExtension
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.tasks.TaskAction

internal abstract class VerifyLocalDependencyLockFileTask : DefaultTask() {
  init {
    description = "Resolves all configurations which indirectly forces the plugin to verify them."
    group = "dependency-lock"

    this.notCompatibleWithConfigurationCache("Resolves configurations at execution time.")
    this.doNotTrackState(
      "It is not possible to inspect the state of this task's inputs before running the task."
    )

    @Suppress("LeakingThis")
    project.dependencyLockingExtension.requiredForTasks.add(this)
  }

  @TaskAction
  fun runTask() {
    project.configurations.resolveAllResolvable()
    project.buildscript.configurations.resolveAllResolvable()
  }

  private fun ConfigurationContainer.resolveAllResolvable() {
    filter { it.isCanBeResolved }.forEach {
      it.incoming.artifactView { isLenient = true }.files.files
    }
  }
}
