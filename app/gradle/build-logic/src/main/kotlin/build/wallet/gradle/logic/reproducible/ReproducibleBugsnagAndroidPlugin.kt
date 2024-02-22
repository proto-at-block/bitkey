package build.wallet.gradle.logic.reproducible

import build.wallet.gradle.logic.gradle.apply
import com.bugsnag.android.gradle.BugsnagFileUploadTask
import com.bugsnag.android.gradle.BugsnagManifestUuidTask
import com.bugsnag.android.gradle.BugsnagPlugin
import com.bugsnag.android.gradle.BugsnagReleasesTask
import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import java.io.File
import java.util.UUID

/**
 * Apply this plugin using `build.wallet.android.bugsnag` ID on an Android application project that
 * needs to support reproducible builds and Bugsnag.
 */
internal class ReproducibleBugsnagAndroidPlugin : Plugin<Project> {
  private val reproducibleBuildVariablesPath = System.getenv("REPRODUCIBLE_BUILD")

  private val isReproducibleBuildTest = System.getenv("REPRODUCIBLE_BUILD_TEST") != null

  private val isNotRealReleaseBuild =
    reproducibleBuildVariablesPath != null || isReproducibleBuildTest

  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply<BugsnagPlugin>()

      val reproducibleBuildVariables =
        reproducibleBuildVariablesPath
          ?.let { parseReproducibleBuildVariables(it) }
          ?: createFreshBuildVariables()

      configureBugsnagUuid(reproducibleBuildVariables)

      configureInputOfGenerateBuildVariablesTasks(reproducibleBuildVariables)

      if (isNotRealReleaseBuild) {
        disableBugsnagFileUploadTask()

        disableBugsnagReleasesTask()
      }
    }
  }

  private fun parseReproducibleBuildVariables(path: String): ReproducibleBuildVariables {
    val file = File(path)

    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(file) as Map<String, String>

    return ReproducibleBuildVariables(json.toMutableMap())
  }

  private fun createFreshBuildVariables(): ReproducibleBuildVariables =
    ReproducibleBuildVariables().apply {
      bugsnagId = UUID.randomUUID().toString()
    }

  private fun Project.configureBugsnagUuid(reproducibleBuildVariables: ReproducibleBuildVariables) {
    afterEvaluate {
      tasks.withType<BugsnagManifestUuidTask>().configureEach {
        buildUuid.set(reproducibleBuildVariables.bugsnagId)
      }
    }
  }

  private fun Project.configureInputOfGenerateBuildVariablesTasks(
    reproducibleBuildVariables: ReproducibleBuildVariables,
  ) {
    tasks.withType<GenerateReproducibleBuildVariablesFileTask>().configureEach {
      this.reproducibleBuildVariables.set(reproducibleBuildVariables.data)
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
