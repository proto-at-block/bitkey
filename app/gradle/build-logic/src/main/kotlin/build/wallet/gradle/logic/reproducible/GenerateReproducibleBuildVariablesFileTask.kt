package build.wallet.gradle.logic.reproducible

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

internal abstract class GenerateReproducibleBuildVariablesFileTask : DefaultTask() {
  @get:Input
  abstract val reproducibleBuildVariables: MapProperty<String, String>

  @get:OutputFile
  abstract val reproducibleBuildVariablesFile: RegularFileProperty

  init {
    description =
      "Generates file that contains variables needed for reproducing the given Android build."
  }

  @TaskAction
  fun runTask() {
    val file = reproducibleBuildVariablesFile.get().asFile

    file.parentFile.mkdirs()

    val json = JsonOutput.toJson(reproducibleBuildVariables.get())
    val readableJson = JsonOutput.prettyPrint(json)

    file.writeText(readableJson)
  }
}
