package build.wallet.gradle.logic

import build.wallet.gradle.logic.extensions.BuildLogicExtension
import build.wallet.gradle.logic.structure.ModuleStructure
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.internal.IdePlugin
import java.io.File

internal class BasePlugin : Plugin<Project> {
  override fun apply(target: Project) =
    target.run {
      // Register `buildLogic { }` dsl.
      extensions.create<BuildLogicExtension>("buildLogic")

      ModuleStructure(this).configure()

      configureBuildDir()
    }

  /**
   * We are using `build.wallet` package naming which conflicts with `.gitignore` rule of
   * denylisting Gradle's `build` directories. Renaming build directories to make it
   * easier to allowlist `build.wallet` package naming.
   *
   * Also see https://github.com/squareup/wallet/issues/727.
   */
  private fun Project.configureBuildDir() {
    layout.buildDirectory.set(File("_build"))

    // Do not let IDE to index build cache directories to speed up the IDE.
    project.plugins.withType<IdePlugin>().configureEach {
      project.extensions.getByType<IdeaModel>().module {
        excludeDirs.add(layout.buildDirectory.get().asFile)
      }
    }
  }
}
