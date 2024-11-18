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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

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
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
      compilerOptions {
        // expect/actual classes are still in Beta: https://kotlinlang.org/docs/multiplatform-expect-actual.html#expected-and-actual-classes
        // We use expect/actual classes extensively, so let's opt-in to suppress warnings.
        freeCompilerArgs.add("-Xexpect-actual-classes")

        if (project.composeCompilerMetricsEnabled()) {
          project.configureComposeCompilerMetrics(this)
        }

        if (project.kotlinCompilerProfilerEnabled()) {
          freeCompilerArgs.add("-Xprofile-phases")
        }

        when (this) {
          is KotlinJvmCompilerOptions -> {
            jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
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
  private fun Project.configureComposeCompilerMetrics(kotlinOptions: KotlinCommonCompilerOptions) {
    kotlinOptions.apply {
      freeCompilerArgs.addAll(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${composeCompilerMetricsDir()}"
      )
      freeCompilerArgs.addAll(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${composeCompilerMetricsDir()}"
      )
    }
  }

  private fun Project.kotlinCompilerProfilerEnabled(): Boolean =
    project.findProperty("build.wallet.ksp.enableProfiler") == "true"

  private fun Project.composeCompilerMetricsEnabled(): Boolean =
    project.findProperty("enableComposeMetrics") == "true"

  private fun Project.composeCompilerMetricsDir() =
    "${layout.buildDirectory.get().asFile.absolutePath}/compose_metrics"
}
