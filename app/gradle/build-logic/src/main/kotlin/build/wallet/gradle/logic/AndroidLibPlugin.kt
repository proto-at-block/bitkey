package build.wallet.gradle.logic

import build.wallet.gradle.dependencylocking.DependencyLockingPlugin
import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.structure.namespace
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Action
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

      android {
        namespace = "build.wallet.${project.namespace}"
        commonConfiguration(project)
      }
    }
}

private fun Project.android(configure: Action<LibraryExtension>): Unit =
  extensions.configure("android", configure)
