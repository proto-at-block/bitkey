package build.wallet.gradle.dependencylocking.validation

import build.wallet.gradle.dependencylocking.configuration.ConfigurationWithOrigin
import build.wallet.gradle.dependencylocking.configuration.DependencyLockingConfig
import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration.Origin
import build.wallet.gradle.dependencylocking.configuration.LockableConfigurationFactory
import build.wallet.gradle.dependencylocking.configuration.LockableVariantFactory
import build.wallet.gradle.dependencylocking.lockfile.LockFile
import build.wallet.gradle.dependencylocking.service.ArtifactHashProvider
import build.wallet.gradle.dependencylocking.service.LockFileProvider
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider

internal class ConfigurationValidator(
  private val project: Project,
  private val dependencyLockingConfigProvider: Provider<DependencyLockingConfig>,
  lockFileProvider: Provider<LockFileProvider>,
  artifactHashProvider: Provider<ArtifactHashProvider>,
) {
  private var activeTasksWhenReady: List<Task>? = null

  private val dependencyLockingConfig by lazy {
    dependencyLockingConfigProvider.get()
  }

  private val lockableConfigurationFactory by lazy {
    LockableConfigurationFactory(
      lockableVariantFactory = LockableVariantFactory(artifactHashProvider.get()),
      dependencyLockingConfig = dependencyLockingConfig
    )
  }

  private val lockFile: LockFile by lazy {
    lockFileProvider.get().lockFile
  }

  init {
    project.gradle.taskGraph.whenReady {
      activeTasksWhenReady = allTasks
    }
  }

  fun validateLazily(
    configuration: Configuration,
    configurationOrigin: Origin,
  ) {
    val activeTasksWhenReady = activeTasksWhenReady

    if (activeTasksWhenReady != null) {
      validate(configuration, configurationOrigin, activeTasksWhenReady)
    } else {
      project.gradle.taskGraph.whenReady {
        validate(configuration, configurationOrigin, allTasks)
      }
    }
  }

  private fun validate(
    configuration: Configuration,
    configurationOrigin: Origin,
    allTasks: List<Task>,
  ) {
    val configurationWithOrigin = ConfigurationWithOrigin(configuration, configurationOrigin)

    if (!isValidationEnabled(configurationWithOrigin, allTasks)) {
      return
    }

    val lockableConfiguration = lockableConfigurationFactory.create(configurationWithOrigin)

    project.logger.info("Validating dependencies of configuration '${configurationWithOrigin.id}'.")

    lockFile.validate(lockableConfiguration, dependencyLockingConfig)
  }

  private fun isValidationEnabled(
    configurationWithOrigin: ConfigurationWithOrigin,
    allTasks: List<Task>,
  ): Boolean {
    if (!configurationWithOrigin.isLocked) {
      project.logger.info("Skipping validation of configuration '${configurationWithOrigin.id}': This configuration is not locked.")
      return false
    }

    val activeRequiredForTask = allTasks.firstOrNullRequiredForTask()
    if (activeRequiredForTask != null) {
      project.logger.info("Forcing validation of configuration '${configurationWithOrigin.id}': The task graph contains a task '${activeRequiredForTask.path}' for which the validation is required.")
      return true
    }

    if (!dependencyLockingConfig.isEnabled) {
      project.logger.info("Skipping validation of configuration '${configurationWithOrigin.id}': Dependency locking is not enabled.")
      return false
    }

    val activeDisabledForTask = allTasks.firstOrNullDisabledForTask()
    if (activeDisabledForTask != null) {
      project.logger.info("Skipping validation of configuration '${configurationWithOrigin.id}': The task graph contains a task '${activeDisabledForTask.path}' for which the validation is disabled.")
      return false
    }

    return true
  }

  private val ConfigurationWithOrigin.isLocked: Boolean
    get() = dependencyLockingConfig[id].isLocked

  private fun List<Task>.firstOrNullRequiredForTask(): Task? =
    firstOrNull { dependencyLockingConfig.requiredForTaskPaths.contains(it.path) }

  private fun List<Task>.firstOrNullDisabledForTask(): Task? =
    firstOrNull { dependencyLockingConfig.disabledForTaskPaths.contains(it.path) }
}
