package build.wallet.gradle.dependencylocking

import build.wallet.gradle.dependencylocking.configuration.DependencyLockingConfig
import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration
import build.wallet.gradle.dependencylocking.extension.DependencyLockingExtension
import build.wallet.gradle.dependencylocking.extension.dependencyLockingExtension
import build.wallet.gradle.dependencylocking.lockfile.listLockFileSegments
import build.wallet.gradle.dependencylocking.service.ArtifactHashProvider
import build.wallet.gradle.dependencylocking.service.LockFileProvider
import build.wallet.gradle.dependencylocking.task.DebugDependencyLockingGroupsTask
import build.wallet.gradle.dependencylocking.task.DebugGlobalDependencyLockingCollisionsTask
import build.wallet.gradle.dependencylocking.task.DebugLocalDependencyLockingCollisionsTask
import build.wallet.gradle.dependencylocking.task.GenerateLocalDependencyLockFileTask
import build.wallet.gradle.dependencylocking.task.UpdateDependencyLockFileTask
import build.wallet.gradle.dependencylocking.task.VerifyLocalDependencyLockFileTask
import build.wallet.gradle.dependencylocking.validation.ConfigurationValidator
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import java.io.File

/**
 * Plugin to configure custom Gradle dependency locking.
 */
class DependencyLockingPlugin : Plugin<Project> {
  private val Project.lockFileDirectory: File
    get() = rootDir.resolve("gradle-lock")

  private val localLockFileConfigurationName = "localLockFile"

  private val allProjectsLockFilesConfigurationName = "allProjectsLockFiles"

  private val lockFileConfigurationUsageAttributeValue = "lock-file"

  override fun apply(target: Project) {
    with(target) {
      val extension = registerExtension()

      val configProvider = provider { extension.createConfig() }

      val configurationValidator = ConfigurationValidator(
        project = project,
        dependencyLockingConfigProvider = configProvider,
        lockFileProvider = registerLockFileProvider(),
        artifactHashProvider = registerArtifactHashProvider()
      )

      configureBuildDependencyLocking(configurationValidator)

      configureBuildScriptDependencyLocking(configurationValidator)

      registerDebugDependencyLockingGroupsTask(configProvider)

      registerDebugLocalDependencyLockingCollisionsTask(configProvider)

      registerGenerateLocalDependencyLockTask(configProvider)

      registerVerifyDependencyLockFileTask()

      if (project == rootProject) {
        registerDebugGlobalDependencyLockingCollisionsTask()

        registerUpdateDependencyLockTask()

        excludeLockFileFromIDESearch()
      }
    }
  }

  private fun Project.registerExtension(): DependencyLockingExtension =
    extensions.create(
      "customDependencyLocking",
      DependencyLockingExtension::class.java
    )

  private fun Project.registerLockFileProvider(): Provider<LockFileProvider> =
    gradle.sharedServices.registerIfAbsent(
      LockFileProvider.KEY,
      LockFileProvider::class.java
    ) {
      parameters.lockFileDirectory.set(lockFileDirectory)
    }

  private fun Project.registerArtifactHashProvider(): Provider<ArtifactHashProvider> =
    gradle.sharedServices.registerIfAbsent(
      ArtifactHashProvider.KEY,
      ArtifactHashProvider::class.java
    ) {
    }

  private fun Project.configureBuildDependencyLocking(
    configurationValidator: ConfigurationValidator,
  ) {
    lockConfigurations(configurations, LockableConfiguration.Origin.Build, configurationValidator)
  }

  private fun Project.configureBuildScriptDependencyLocking(
    configurationValidator: ConfigurationValidator,
  ) {
    lockConfigurations(
      buildscript.configurations,
      LockableConfiguration.Origin.BuildScript,
      configurationValidator
    )
  }

  private fun lockConfigurations(
    configurationContainer: ConfigurationContainer,
    configurationOrigin: LockableConfiguration.Origin,
    configurationValidator: ConfigurationValidator,
  ) {
    configurationContainer.configureEach {
      if (isCanBeResolved) {
        lockConfiguration(this, configurationOrigin, configurationValidator)
      }
    }
  }

  private fun lockConfiguration(
    configuration: Configuration,
    configurationOrigin: LockableConfiguration.Origin,
    configurationValidator: ConfigurationValidator,
  ) {
    if (configuration.state == Configuration.State.UNRESOLVED) {
      configuration.incoming.afterResolve {
        configurationValidator.validateLazily(configuration, configurationOrigin)
      }
    } else {
      configurationValidator.validateLazily(configuration, configurationOrigin)
    }

    configuration.resolutionStrategy {
      failOnNonReproducibleResolution()
    }
  }

  private fun Project.registerDebugDependencyLockingGroupsTask(
    configProvider: Provider<DependencyLockingConfig>,
  ) {
    tasks.register<DebugDependencyLockingGroupsTask>("debugDependencyLockingGroups") {
      config.set(configProvider)

      dependencyLockingExtension.disabledForTasks.add(this)
    }
  }

  private fun Project.registerDebugLocalDependencyLockingCollisionsTask(
    configProvider: Provider<DependencyLockingConfig>,
  ) {
    tasks.register<DebugLocalDependencyLockingCollisionsTask>("debugLocalDependencyLockingCollisions") {
      config.set(configProvider)

      dependencyLockingExtension.disabledForTasks.add(this)
    }
  }

  private fun Project.registerGenerateLocalDependencyLockTask(
    configProvider: Provider<DependencyLockingConfig>,
  ) {
    val task =
      tasks.register<GenerateLocalDependencyLockFileTask>("generateLocalDependencyLockFile") {
        val lockFileDirectory = layout.buildDirectory.map {
          it.dir("dependency-locking").dir("local-gradle-lock")
        }

        localLockFileDirectory.set(lockFileDirectory)

        config.set(configProvider)

        dependencyLockingExtension.disabledForTasks.add(this)
      }

    configurations.create(localLockFileConfigurationName) {
      isCanBeConsumed = true
      isCanBeResolved = false
      description = "Used for passing local lock file to the global updateDependencyLock task."

      attributes {
        attribute(
          Usage.USAGE_ATTRIBUTE,
          objects.named(lockFileConfigurationUsageAttributeValue)
        )
      }
    }

    artifacts {
      add(localLockFileConfigurationName, task.map { it.localLockFileDirectory })
    }
  }

  private fun Project.registerVerifyDependencyLockFileTask() {
    tasks.register<VerifyLocalDependencyLockFileTask>("verifyDependencyLockFileTask") {
    }
  }

  private fun Project.registerDebugGlobalDependencyLockingCollisionsTask() {
    tasks.register<DebugGlobalDependencyLockingCollisionsTask>("debugGlobalDependencyLockingCollisions") {
      val localLockFiles = getAllLocalLockFilesDirectoriesProvider()

      localLockFileSegments.set(localLockFiles)

      rootProjectDirectory.set(rootDir)

      dependencyLockingExtension.disabledForTasks.add(this)
    }
  }

  private fun Project.registerUpdateDependencyLockTask() {
    tasks.register<UpdateDependencyLockFileTask>("updateDependencyLockFile") {
      val lockFileDirectories = getAllLocalLockFilesDirectoriesProvider()

      dependsOn(lockFileDirectories)

      val lockFileSegments = lockFileDirectories.map { directories ->
        files(
          directories.flatMap { it.listLockFileSegments() }
        )
      }

      localLockFileSegments.set(lockFileSegments)

      globalLockFileDirectory.set(lockFileDirectory)

      rootProjectDirectory.set(rootDir)

      dependencyLockingExtension.disabledForTasks.add(this)
    }
  }

  private fun Project.getAllLocalLockFilesDirectoriesProvider(): Provider<FileCollection> {
    val configuration = configurations.findByName(allProjectsLockFilesConfigurationName)
      ?: createAllProjectsLockFilesConfiguration()

    return provider {
      configuration.incoming.artifactView { isLenient = true }.files
    }
  }

  private fun Project.createAllProjectsLockFilesConfiguration(): Configuration {
    val configuration = configurations.create(allProjectsLockFilesConfigurationName) {
      isCanBeConsumed = false
      isCanBeResolved = true
      description = "Used for fetching all local lock files."

      attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(lockFileConfigurationUsageAttributeValue))
      }
    }

    dependencyLockingExtension.apply {
      configuration.isLocked.set(false)
    }

    dependencies {
      allprojects.forEach {
        configuration(it)
      }
    }

    return configuration
  }

  private fun Project.excludeLockFileFromIDESearch() {
    pluginManager.apply(IdeaPlugin::class.java)

    extensions.configure<IdeaModel> {
      module {
        excludeDirs.add(lockFileDirectory)
      }
    }
  }
}
