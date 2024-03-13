package build.wallet.gradle.logic.reproducible

import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.withType
import java.io.File
import java.util.UUID

internal class ReproducibleBuildVariablesPlugin : Plugin<Project> {
  private val reproducibleBuildVariablesPath: String? = System.getenv("REPRODUCIBLE_BUILD")

  override fun apply(target: Project) {
    with(target) {
      val reproducibleBuildVariables = reproducibleBuildVariablesPath
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { parseReproducibleBuildVariables(it) }
        ?: createFreshBuildVariables()

      val extension = extensions.create("reproducibleBuildVariables", ReproducibleBuildVariablesExtension::class.java)
      extension.variables.set(reproducibleBuildVariables)

      configureInputOfGenerateBuildVariablesTasks(reproducibleBuildVariables)
    }
  }

  private fun parseReproducibleBuildVariables(path: String): ReproducibleBuildVariables {
    val file = File(path)

    @Suppress("UNCHECKED_CAST")
    val json = JsonSlurper().parse(file) as Map<String, String>

    return ReproducibleBuildVariables(json.toMutableMap())
  }

  @Suppress("UnstableApiUsage")
  private fun createFreshBuildVariables(): ReproducibleBuildVariables {
    return ReproducibleBuildVariables().apply {
      bugsnagId = UUID.randomUUID().toString()
      emergencyApkHash = ""
      emergencyApkVersion = ""
      emergencyApkUrl = ""
    }
  }

  private fun Project.configureInputOfGenerateBuildVariablesTasks(
    reproducibleBuildVariables: ReproducibleBuildVariables,
  ) {
    tasks.withType<GenerateReproducibleBuildVariablesFileTask>().configureEach {
      this.reproducibleBuildVariables.set(reproducibleBuildVariables.data)
    }
  }
}
