package build.wallet.gradle.dependencylocking.extension

import build.wallet.gradle.dependencylocking.configuration.DependencyLockingConfig
import build.wallet.gradle.dependencylocking.configuration.LockableConfiguration
import build.wallet.gradle.dependencylocking.lockfile.ModuleIdentifierPattern
import build.wallet.gradle.dependencylocking.util.getId
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.domainObjectContainer
import org.gradle.kotlin.dsl.getByType
import javax.inject.Inject

abstract class DependencyLockingExtension
  @Inject
  constructor(
    private val objects: ObjectFactory,
    val project: Project,
  ) {
    val isEnabled: Property<Boolean> =
      objects.property(Boolean::class.java).convention(isLockingEnabledByDefault)

    val ignoredModules: SetProperty<String> = objects.setProperty(String::class.java)

    val disabledForTasks: SetProperty<Task> = objects.setProperty(Task::class.java)

    val requiredForTasks: SetProperty<Task> = objects.setProperty(Task::class.java)

    val dependencyLockingGroups = objects.domainObjectContainer(DependencyLockingGroupConfig::class)

    private val configByConfigurationId =
      mutableMapOf<LockableConfiguration.Id, DependencyLockingConfigurationConfig>()

    val Configuration.isLocked: Property<Boolean>
      get() = getLockingConfigurationForConfiguration(this).isLocked

    val Configuration.dependencyLockingGroup: Property<DependencyLockingGroupConfig>
      get() = getLockingConfigurationForConfiguration(this).dependencyLockingGroup

    private val isLockingEnabledByDefault: Boolean
      get() = project.findProperty("build.wallet.dependency-locking.is-enabled") != "false"

    internal fun createConfig(): DependencyLockingConfig =
      DependencyLockingConfig(
        isEnabled = isEnabled.get(),
        projectPath = project.path,
        ignoredModules = ignoredModules.get().map(::ModuleIdentifierPattern),
        disabledForTaskPaths = disabledForTasks.get().map { it.path },
        requiredForTaskPaths = requiredForTasks.get().map { it.path },
        configurationsConfig = configByConfigurationId.mapValues { it.value.createConfig() }
      )

    private fun getLockingConfigurationForConfiguration(
      configuration: Configuration,
    ): DependencyLockingConfigurationConfig {
      // In Gradle 9+, configurations become immutable earlier.
      // Even accessing certain properties on resolved configurations can trigger errors.
      // Use configuration name as key since it's always safe to access.
      val configKey = try {
        configuration.id
      } catch (_: IllegalStateException) {
        // Fall back to using name-based key for already-resolved configurations
        LockableConfiguration.Id(LockableConfiguration.Origin.Build, configuration.name)
      }

      return configByConfigurationId.getOrPut(configKey) {
        val config = objects.newInstance(DependencyLockingConfigurationConfig::class.java)
        // In Gradle 9+, configurations can be "observed" (not mutable) even when state == UNRESOLVED.
        // The only safe way to check is to try and catch the exception.
        try {
          config.initWithConfiguration(configuration)
        } catch (_: IllegalStateException) {
          // Configuration was already observed/resolved, skip initialization
        } catch (_: org.gradle.api.InvalidUserCodeException) {
          // Configuration was already observed/resolved, skip initialization
        }
        config
      }
    }

    private val Configuration.id: LockableConfiguration.Id
      get() = getId(origin)

    private val Configuration.origin: LockableConfiguration.Origin
      get() = when (this) {
        in project.buildscript.configurations -> LockableConfiguration.Origin.BuildScript
        in project.configurations -> LockableConfiguration.Origin.Build
        else -> error("Configuration $name belongs to unsupported ConfigurationContainer.")
      }
  }

internal val Project.dependencyLockingExtension: DependencyLockingExtension
  get() = extensions.getByType<DependencyLockingExtension>()
