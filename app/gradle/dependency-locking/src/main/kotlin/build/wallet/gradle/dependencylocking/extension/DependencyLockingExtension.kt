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
    ): DependencyLockingConfigurationConfig =
      configByConfigurationId.getOrPut(configuration.id) {
        objects.newInstance(DependencyLockingConfigurationConfig::class.java, configuration)
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
