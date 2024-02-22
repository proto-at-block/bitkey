package build.wallet.gradle.dependencylocking

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

internal abstract class UpdateDependencyLockTask : DefaultTask() {
  init {
    description = "Updates Gradle lock files in the given module."

    this.notCompatibleWithConfigurationCache("Resolves configurations at execution time.")
    this.doNotTrackState(
      "It is not possible to inspect the state of this task's inputs before running the task."
    )
  }

  @TaskAction
  fun runTask() {
    check(project.gradle.startParameter.isWriteDependencyLocks) {
      "Task $name must be run with '--write-locks' flag. For example: `gradle $name --write-locks`"
    }

    project.configurations
      .filter { it.isCanBeResolved }
      .forEach {
        it.incoming.resolutionResult.rootComponent.get()
      }
  }
}
