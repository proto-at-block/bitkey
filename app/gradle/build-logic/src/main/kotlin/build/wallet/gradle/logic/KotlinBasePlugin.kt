package build.wallet.gradle.logic

import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.gradle.libs
import build.wallet.gradle.logic.structure.isImplModule
import build.wallet.gradle.logic.structure.isPublicModule
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

/**
 * Contains common Kotlin build configurations.
 */
internal class KotlinBasePlugin : Plugin<Project> {
  override fun apply(target: Project) =
    target.run {
      configureToolchain()
      configureKotlinCompilerOptions()

      if (isPublicModule() || isImplModule()) {
        pluginManager.apply<LintDetektPlugin>()
      }
    }

  private fun Project.configureKotlinCompilerOptions() {
    tasks.withType<KotlinCompile<*>>().configureEach {
      kotlinOptions {
        if (project.composeCompilerMetricsEnabled()) {
          project.configureComposeCompilerMetrics(this)
        }

        when (this) {
          is KotlinJvmOptions -> {
            jvmTarget = libs.versions.jvmTarget.get()
          }
        }
      }
    }
  }

  private fun Project.configureToolchain() {
    configure<JavaPluginExtension> {
      val jvmTarget = libs.versions.jvmTarget.get().toInt()
      targetCompatibility = JavaVersion.toVersion(jvmTarget)
    }

    extensions.findByType<KotlinProjectExtension>()?.apply {
      val jvmToolchain = libs.versions.jvmToolchain.get().toInt()
      jvmToolchain(jvmToolchain)
    }
  }

  /**
   * To enable Compose Compiler metrics, use `enableComposeMetrics` parameter:
   * `gradle :app:assembleRelease -PenableComposeMetrics=true`
   *
   * Reports are stored at `foo-module/build_/compose_metrics/` directory.
   *
   * https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md
   */
  private fun Project.configureComposeCompilerMetrics(kotlinOptions: KotlinCommonOptions) {
    kotlinOptions.apply {
      freeCompilerArgs = freeCompilerArgs +
        listOf(
          "-P",
          "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${composeCompilerMetricsDir()}"
        )
      freeCompilerArgs = freeCompilerArgs +
        listOf(
          "-P",
          "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${composeCompilerMetricsDir()}"
        )
    }
  }

  private fun Project.composeCompilerMetricsEnabled(): Boolean =
    project.findProperty("enableComposeMetrics") == "true"

  private fun Project.composeCompilerMetricsDir() =
    "${layout.buildDirectory.get().asFile.absolutePath}/compose_metrics"
}
