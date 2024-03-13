package build.wallet.gradle.dependencylocking.task

import build.wallet.gradle.dependencylocking.configuration.ConfigurationWithOrigin
import build.wallet.gradle.dependencylocking.configuration.DependencyLockingConfig
import build.wallet.gradle.dependencylocking.configuration.DependencyLockingGroup
import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration
import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration.Origin
import build.wallet.gradle.dependencylocking.configuration.LockableConfigurationFactory
import build.wallet.gradle.dependencylocking.configuration.LockableVariantFactory
import build.wallet.gradle.dependencylocking.lockfile.LockFile
import build.wallet.gradle.dependencylocking.lockfile.LockFileUnificationResult
import build.wallet.gradle.dependencylocking.lockfile.unifyWith
import build.wallet.gradle.dependencylocking.service.ArtifactHashProvider
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

internal abstract class DebugLocalDependencyLockingCollisionsTask : DefaultTask() {
  @get:Input
  abstract val config: Property<DependencyLockingConfig>

  @Suppress("UnstableApiUsage")
  @get:ServiceReference(ArtifactHashProvider.KEY)
  abstract val artifactHashProvider: Property<ArtifactHashProvider>

  private val lockableConfigurationFactory by lazy {
    LockableConfigurationFactory(
      lockableVariantFactory = LockableVariantFactory(artifactHashProvider.get()),
      dependencyLockingConfig = config.get()
    )
  }

  init {
    description =
      "Prints information about conflicts that prevent the creation of a unified module lock file."
    group = "dependency-lock"

    this.notCompatibleWithConfigurationCache("Uses configurations at execution time.")
    this.doNotTrackState("Not worth caching.")
  }

  @TaskAction
  fun runTask() {
    val configurationsWithOrigin =
      project.buildscript.configurations.map { ConfigurationWithOrigin(it, Origin.BuildScript) } +
        project.configurations.map { ConfigurationWithOrigin(it, Origin.Build) }

    printConfigurations(configurationsWithOrigin)
  }

  private fun printConfigurations(configurationsWithOrigin: List<ConfigurationWithOrigin>) {
    val lockFileGroups = configurationsWithOrigin
      .filter { it.configuration.isCanBeResolved }
      .mapNotNull { lockableConfigurationFactory.createOrNull(it) }
      .groupBy { it.group.name }
      .values
      .flatMap { it.groupByConflicts() }

    printLockFileGroups(lockFileGroups)

    printLockFileGroupsDifferences(lockFileGroups)
  }

  private fun List<LockableConfiguration>.groupByConflicts(): List<LockFileGroup> {
    val lockFileGroups = mutableListOf<LockFileGroup>()

    forEach {
      lockFileGroups.addNextConfiguration(it)
    }

    return lockFileGroups
  }

  private fun MutableList<LockFileGroup>.addNextConfiguration(
    lockableConfiguration: LockableConfiguration,
  ) {
    forEach {
      val wasAdded = it.tryAddConfiguration(lockableConfiguration)
      if (wasAdded) {
        return
      }
    }

    add(
      LockFileGroup(size, lockableConfiguration)
    )
  }

  private fun printLockFileGroups(lockFileGroups: List<LockFileGroup>) {
    lockFileGroups.forEach {
      println("Group: ${it.sharedDependencyLockingGroup}:")

      printLockFileGroup(it)
    }
  }

  private fun printLockFileGroup(lockFileGroup: LockFileGroup) {
    lockFileGroup.containedConfigurations
      .sortedBy { it.id.toString() }
      .forEach {
        println("     ${it.id}")
      }

    println()
    println()
  }

  private fun printLockFileGroupsDifferences(lockFileGroups: List<LockFileGroup>) {
    if (lockFileGroups.size <= 1) {
      return
    }

    println("Differences:")
    println()

    lockFileGroups.forEachIndexed { lhsIndex, lhsLockFileGroup ->
      println("'${lhsLockFileGroup.sharedDependencyLockingGroup.name}':")

      lockFileGroups.forEachIndexed { rhsIndex, rhsLockFileGroup ->
        if (lhsIndex < rhsIndex) {
          printDifferences(lhsLockFileGroup, rhsLockFileGroup)
        }
      }

      println()
    }
  }

  private fun printDifferences(
    lhsLockFileGroup: LockFileGroup,
    rhsLockFileGroup: LockFileGroup,
  ) {
    val result = lhsLockFileGroup.unifiedLockFile.unifyWith(
      rhsLockFileGroup.unifiedLockFile(lhsLockFileGroup.sharedDependencyLockingGroup)
    )

    print("    '${rhsLockFileGroup.sharedDependencyLockingGroup.name}': ")

    when (result) {
      is LockFileUnificationResult.Success -> {
        println("Success")
      }
      is LockFileUnificationResult.Error -> {
        println(result)
      }
    }
  }

  private class LockFileGroup(
    val index: Int,
    initialLockableConfiguration: LockableConfiguration,
  ) {
    val sharedDependencyLockingGroup = DependencyLockingGroup.Known(
      name = initialLockableConfiguration.group.name + ("-$index".takeIf { index > 0 } ?: "")
    )

    var unifiedLockFile: LockFile = LockFile()
      private set

    val containedConfigurations = mutableListOf<LockableConfiguration>()

    init {
      tryAddConfiguration(initialLockableConfiguration)
    }

    fun unifiedLockFile(dependencyLockingGroup: DependencyLockingGroup): LockFile {
      return containedConfigurations.fold(LockFile()) { acc, lockableConfiguration ->
        val nextLockFile =
          lockableConfiguration.copy(group = dependencyLockingGroup).createLockFile()

        (acc.unifyWith(nextLockFile) as LockFileUnificationResult.Success).value
      }
    }

    fun tryAddConfiguration(lockableConfiguration: LockableConfiguration): Boolean {
      val substitutedConfiguration = lockableConfiguration.copy(
        group = sharedDependencyLockingGroup
      )

      val configurationLockFile = substitutedConfiguration.createLockFile()

      when (val unificationResult = unifiedLockFile.unifyWith(configurationLockFile)) {
        is LockFileUnificationResult.Success -> {
          unifiedLockFile = unificationResult.value

          containedConfigurations.add(substitutedConfiguration)

          return true
        }
        is LockFileUnificationResult.Error -> {
          return false
        }
      }
    }
  }
}
