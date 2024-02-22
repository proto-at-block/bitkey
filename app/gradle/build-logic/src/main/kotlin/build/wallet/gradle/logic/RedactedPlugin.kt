package build.wallet.gradle.logic

import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.gradle.implementation
import build.wallet.gradle.logic.gradle.kotlin
import build.wallet.gradle.logic.gradle.libs
import build.wallet.gradle.logic.gradle.sourceSets
import dev.zacsweers.redacted.gradle.RedactedGradleSubplugin
import org.gradle.api.Plugin
import org.gradle.api.Project

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
}
