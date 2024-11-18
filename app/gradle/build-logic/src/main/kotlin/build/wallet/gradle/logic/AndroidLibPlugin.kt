package build.wallet.gradle.logic

import build.wallet.gradle.dependencylocking.DependencyLockingCommonGroupConfigurationPlugin
import build.wallet.gradle.dependencylocking.DependencyLockingPlugin
import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.structure.namespace
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Apply this plugin using `build.wallet.android.lib` ID on an Android library project that needs to
 * compile Kotlin.
 */
internal class AndroidLibPlugin : Plugin<Project> {
  override fun apply(target: Project) =
    target.run {
      pluginManager.apply("com.android.library")
      pluginManager.apply<BasePlugin>()
      pluginManager.apply<KotlinBasePlugin>()
      pluginManager.apply<DependencyLockingPlugin>()
      pluginManager.apply<DependencyLockingCommonGroupConfigurationPlugin>()
      pluginManager.apply<DependencyLockingDependencyConfigurationPlugin>()
      pluginManager.apply<AutomaticKotlinOptInPlugin>()

      androidLib {
        namespace = "build.wallet.${project.namespace}"
        commonConfiguration(project)
        buildFeatures {
          // Most of the modules do not use Resources - disable by default.
          // Resources can be enabled using `buildLogic.android {}` extension.
          androidResources = false
        }
      }
    }
}
