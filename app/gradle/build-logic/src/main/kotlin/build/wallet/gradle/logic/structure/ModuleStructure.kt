package build.wallet.gradle.logic.structure

import build.wallet.gradle.logic.gradle.api
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

/**
 * Enforces minimalist module structure:
 * - `:public` modules can only depend on libraries and other `:public` modules.
 * - `:impl` modules can only depend on libraries and other `:public` modules.
 * - `:impl` modules receive transitive dependency on sibling `:public` by default.
 */
internal class ModuleStructure(private val project: Project) {
  fun configure() {
    project.addPublicModuleIfNeeded()
  }

  /**
   * Add sibling `:public` module automatically to non-public modules.
   * For example, `:feature-a:impl` will get transitive dependency on `:feature-a:public`.
   */
  private fun Project.addPublicModuleIfNeeded() {
    if (isImplModule() || isFakeModule() || isTestingModule()) {
      val siblingPublicModule = siblingModules["public"] ?: return
      if (pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
        kotlinExtension.sourceSets.apply {
          val commonMain by getting {
            dependencies {
              api(project(siblingPublicModule.path))
            }
          }
        }
      } else {
        dependencies {
          api(project(siblingPublicModule.path))
        }
      }
    }
  }

  private val Project.siblingModules: Map<String, Project> get() {
    val parentGroupName = name.substringBeforeLast("-")
    return parent?.childProjects.orEmpty().filterKeys {
      it.startsWith(parentGroupName)
    }.mapKeys { (key, _) ->
      key.substringAfterLast("-")
    }
  }
}
