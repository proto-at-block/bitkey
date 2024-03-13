package build.wallet.gradle.dependencylocking.task

import build.wallet.gradle.dependencylocking.configuration.ConfigurationWithOrigin
import build.wallet.gradle.dependencylocking.configuration.DependencyLockingConfig
import build.wallet.gradle.dependencylocking.configuration.DependencyLockingGroup
import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration.Origin
import build.wallet.gradle.dependencylocking.util.getId
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

internal abstract class DebugDependencyLockingGroupsTask : DefaultTask() {
  @get:Input
  abstract val config: Property<DependencyLockingConfig>

  init {
    description = "Lists all configurations grouped by their assigned dependency locking group."
    group = "dependency-lock"

    this.notCompatibleWithConfigurationCache("Uses configurations at execution time.")
    this.doNotTrackState("Not worth caching.")
  }

  @TaskAction
  fun runTask() {
    val configurationsWithOrigin =
      project.buildscript.configurations.map { ConfigurationWithOrigin(it, Origin.BuildScript) } +
        project.configurations.map { ConfigurationWithOrigin(it, Origin.Build) }

    printConfigurations(configurationsWithOrigin, config.get())
  }

  private fun printConfigurations(
    configurationsWithOrigin: List<ConfigurationWithOrigin>,
    config: DependencyLockingConfig,
  ) {
    configurationsWithOrigin
      .filter { it.configuration.isCanBeResolved }
      .groupBy { config[it.id].group }
      .toList()
      .sortedBy { it.first.name }
      .forEach {
        printConfigurationsForGroup(it.first, it.second, config)
      }
  }

  private fun printConfigurationsForGroup(
    group: DependencyLockingGroup,
    configurationsWithOrigin: List<ConfigurationWithOrigin>,
    config: DependencyLockingConfig,
  ) {
    println(group.name + ":")

    configurationsWithOrigin
      .sortedBy { it.configuration.name }
      .forEach { configurationWithOrigin ->
        if (config[configurationWithOrigin.id].isLocked) {
          println("    ${configurationWithOrigin.id} - ${configurationWithOrigin.configuration.description}")
        } else {
          println("    ${configurationWithOrigin.id} (not locked!) - ${configurationWithOrigin.configuration.description}")
        }

        printConfigurationParents(configurationWithOrigin, config)
      }

    println()
  }

  private fun printConfigurationParents(
    configurationWithOrigin: ConfigurationWithOrigin,
    config: DependencyLockingConfig,
  ) {
    configurationWithOrigin.configuration.getAllParentsRecursively()
      .sortedBy { it.name }
      .forEach { parentConfiguration ->
        val groupName = if (parentConfiguration.isCanBeResolved) {
          config[parentConfiguration.getId(configurationWithOrigin.origin)].group.name
        } else {
          "<not-resolvable>"
        }

        println("        ${parentConfiguration.name} ($groupName) - ${parentConfiguration.description}")
      }
  }

  private fun Configuration.getAllParentsRecursively(): Set<Configuration> =
    (extendsFrom + extendsFrom.flatMap { it.getAllParentsRecursively() }).toSet()
}
