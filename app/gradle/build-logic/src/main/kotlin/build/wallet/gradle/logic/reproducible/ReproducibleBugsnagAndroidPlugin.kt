package build.wallet.gradle.logic.reproducible

import build.wallet.gradle.logic.gradle.apply
import com.bugsnag.android.gradle.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Apply this plugin using `build.wallet.android.bugsnag` ID on an Android application project that
 * needs to support reproducible builds and Bugsnag.
 */
internal class ReproducibleBugsnagAndroidPlugin : Plugin<Project> {
  private val uploadBugsnagMapping = System.getenv("UPLOAD_BUGSNAG_MAPPING")?.toBoolean() ?: false

  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply<BugsnagPlugin>()
      pluginManager.apply<ReproducibleBuildVariablesPlugin>()

      val reproducibleBuildVariables = extensions.getByType(ReproducibleBuildVariablesExtension::class.java).variables.get()
      configureBugsnagUuid(reproducibleBuildVariables)

      extensions.configure<BugsnagPluginExtension> {
        requestTimeoutMs.set(360000L)
      }

      if (!uploadBugsnagMapping) {
        disableBugsnagFileUploadTask()

        disableBugsnagReleasesTask()
      }
    }
  }

  private fun Project.configureBugsnagUuid(
    reproducibleBuildVariables: ReproducibleBuildVariables,
  ) {
    afterEvaluate {
      tasks.withType<BugsnagManifestUuidTask>().configureEach {
        buildUuid.set(reproducibleBuildVariables.bugsnagId)
      }
    }
  }

  private fun Project.disableBugsnagFileUploadTask() {
    tasks.withType<BugsnagFileUploadTask>().configureEach {
      enabled = false
    }
  }

  private fun Project.disableBugsnagReleasesTask() {
    tasks.withType<BugsnagReleasesTask>().configureEach {
      enabled = false
    }
  }
}
