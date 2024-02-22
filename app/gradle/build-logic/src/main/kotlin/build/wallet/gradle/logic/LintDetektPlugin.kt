package build.wallet.gradle.logic

import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.gradle.detektPlugin
import build.wallet.gradle.logic.gradle.libs
import com.squareup.wire.gradle.WireTask
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

/**
 * Plugin for applying and configuring Detekt Gradle plugin.
 * https://detekt.dev/docs/intro
 */
internal class LintDetektPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.run {
      pluginManager.apply<DetektPlugin>()

      // Analyze Kotlin files only
      val detektIncludes =
        listOf(
          "**/*.kt",
          "**/*.kts"
        )

      val detektExcludes =
        listOf(
          // Build cache directory
          ".*/_build/".toRegex()
        )

      // Common plugin configuration
      detekt {
        autoCorrect = false
        parallel = true
        config.from(rootProject.file("detekt/config.yml"))
        buildUponDefaultConfig = true
      }

      // TODO(W-1784): generate serif report and add to GHA
      // Tasks to be used for report merging below
      // val reportMerge by tasks.registering(ReportMergeTask::class) {
      //   output.set(rootProject.buildDir.resolve("reports/detekt/merged_report.sarif"))
      // }

      // Configure plugins with additional rules
      if (project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
        kotlinExtension.sourceSets.apply {
          dependencies {
            detektPlugin(libs.pluginClasspath.detekt.compose)
          }
        }
      } else {
        dependencies {
          detektPlugin(libs.pluginClasspath.detekt.compose)
        }
      }

      // Configure Detekt Gradle task
      tasks.withType<Detekt>().configureEach {
        include(detektIncludes)
        // Simply calling `exclude(detektExcludes)` doesn't work: https://github.com/detekt/detekt/issues/4127.
        exclude { spec ->
          detektExcludes.any { excluded -> spec.file.absolutePath.contains(excluded) }
        }

        // TODO(W-1784): generate serif report and add to GHA
        // Configure reports
        reports {
          html.required.set(false)
          sarif.required.set(false)
          xml.required.set(false)
          txt.required.set(false)
          md.required.set(false)
        }

        // TODO(W-1784): generate serif report and add to GHA
        // Configure Sarif report merging
        // finalizedBy(reportMerge)
        // reportMerge.configure {
        //   input.from(sarifReportFile)
        // }

        // TODO(W-1786): prevents race condition detected by Gradle. Remove this hack after Wire
        //               plugin is updated.
        dependsOn(tasks.withType<WireTask>())
      }

      // Create umbrella task
      // TODO: remove once provided out of the box by Detekt: https://github.com/detekt/detekt/issues/3838
      tasks.register("detektAll") {
        group = VERIFICATION_GROUP
        dependsOn(tasks.withType<Detekt>())
      }
    }
  }
}

private fun Project.detekt(configure: DetektExtension.() -> Unit) = extensions.configure(configure)
