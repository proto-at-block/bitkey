package build.wallet.gradle.logic

import build.wallet.gradle.logic.extensions.systemProperty
import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.gradle.implementation
import build.wallet.gradle.logic.gradle.kotlin
import build.wallet.gradle.logic.gradle.libs
import build.wallet.gradle.logic.gradle.sourceSets
import dev.zacsweers.redacted.gradle.RedactedGradleSubplugin
import dev.zacsweers.redacted.gradle.RedactedPluginExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Custom plugin to apply Redacted Gradle plugin.
 *
 * Automatically adds redacted-annotations dependency, which addresses a bug in the plugin:
 * https://github.com/ZacSweers/redacted-compiler-plugin/issues/172 (W-5492).
 */
internal class RedactedPlugin : Plugin<Project> {
  override fun apply(target: Project) =
    target.run {
      pluginManager.apply<RedactedGradleSubplugin>()
      if (isIntelliJ()) {
        extensions.configure(RedactedPluginExtension::class) {
          // Set this to false if you're having issues with redaction interfering with test debugging
          enabled.set(true)
        }
      }
      kotlin {
        sourceSets {
          commonMain {
            dependencies {
              implementation(libs.kmp.redacted.annotations)
            }
          }
        }
      }
    }

  private fun Project.isIntelliJ(): Boolean = systemProperty("idea.active").isPresent
}
