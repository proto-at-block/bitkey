package build.wallet.gradle.dependencylocking.task

import build.wallet.gradle.dependencylocking.configuration.ConfigurationWithOrigin
import build.wallet.gradle.dependencylocking.configuration.DependencyLockingConfig
import build.wallet.gradle.dependencylocking.configuration.DependencyLockingGroup
import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration
import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration.Origin
import build.wallet.gradle.dependencylocking.configuration.LockableConfigurationFactory
import build.wallet.gradle.dependencylocking.configuration.LockableVariantFactory
import build.wallet.gradle.dependencylocking.exception.LockFileUnificationException
import build.wallet.gradle.dependencylocking.exception.UndefinedDependencyLockingGroupException
import build.wallet.gradle.dependencylocking.lockfile.LockFile
import build.wallet.gradle.dependencylocking.lockfile.LockFileUnificationResult
import build.wallet.gradle.dependencylocking.lockfile.serializeTo
import build.wallet.gradle.dependencylocking.lockfile.unifyWith
import build.wallet.gradle.dependencylocking.service.ArtifactHashProvider
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

internal abstract class GenerateLocalDependencyLockFileTask : DefaultTask() {
  @get:Input
  abstract val config: Property<DependencyLockingConfig>

  @get:OutputDirectory
  abstract val localLockFileDirectory: DirectoryProperty

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
      "Generates temporary local dependency lock file that is used to construct the global lock file."
    group = "dependency-lock"

    this.notCompatibleWithConfigurationCache("Resolves configurations at execution time.")
    this.doNotTrackState(
      "It is not possible to inspect the state of this task's inputs before running the task."
    )
  }

  @TaskAction
  fun runTask() {
    val buildLockFile = createPartialLockFile(project.configurations, Origin.Build)
    val buildScriptLockFile = createPartialLockFile(
      project.buildscript.configurations,
      Origin.BuildScript
    )

    val lockFile = unifyLockFilesWithDifferentOrigins(buildLockFile, buildScriptLockFile)

    lockFile.serializeTo(localLockFileDirectory.get().asFile)
  }

  private fun createPartialLockFile(
    configurationContainer: ConfigurationContainer,
    origin: Origin,
  ): LockFile {
    val configurations = configurationContainer.createLockableConfigurations(origin)

    configurations.forEach {
      it.verifyHasKnownGroup()
    }

    return configurations.createLockFile()
  }

  private fun ConfigurationContainer.createLockableConfigurations(
    origin: Origin,
  ): List<LockableConfiguration> =
    this.filter { it.isCanBeResolved }
      .mapNotNull { lockableConfigurationFactory.createOrNull(ConfigurationWithOrigin(it, origin)) }

  private fun LockableConfiguration.verifyHasKnownGroup() {
    if (this.group !is DependencyLockingGroup.Known) {
      throw UndefinedDependencyLockingGroupException(this)
    }
  }

  private fun List<LockableConfiguration>.createLockFile(): LockFile =
    fold(LockFile()) { acc, configuration ->
      when (val result = acc.unifyWith(configuration.createLockFile())) {
        is LockFileUnificationResult.Success -> result.value
        is LockFileUnificationResult.Error -> {
          throw createUnificationException(result, configuration)
        }
      }
    }

  private fun createUnificationException(
    result: LockFileUnificationResult.Error,
    configuration: LockableConfiguration,
  ): LockFileUnificationException =
    LockFileUnificationException(
      error = result,
      lhsLockFileDescription = "other configurations",
      rhsLockFileDescription = "configuration ${configuration.id}"
    )

  private fun unifyLockFilesWithDifferentOrigins(
    buildLockFile: LockFile,
    buildScriptLockFile: LockFile,
  ): LockFile =
    when (val result = buildLockFile.unifyWith(buildScriptLockFile)) {
      is LockFileUnificationResult.Success -> result.value
      is LockFileUnificationResult.Error -> {
        throw LockFileUnificationException(
          error = result,
          lhsLockFileDescription = "build configurations",
          rhsLockFileDescription = "buildscript configurations"
        )
      }
    }
}
