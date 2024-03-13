package build.wallet.gradle.logic.reproducible

import build.wallet.gradle.logic.rust.util.withName
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.gradle.AppExtension
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register

fun ApplicationBuildType.reproducibleBuildVariables(project: Project) {
  val buildTypeName = name
  val capitalizedBuildTypeName = buildTypeName.replaceFirstChar { it.uppercase() }

  val generateVariablesTask =
    project.configureGenerateVariablesTask(
      capitalizedBuildTypeName,
      buildTypeName
    )

  project.configureResourcesDependency(
    capitalizedBuildTypeName,
    generateVariablesTask,
    buildTypeName
  )
}

private fun Project.configureGenerateVariablesTask(
  capitalizedBuildTypeName: String,
  buildTypeName: String,
): TaskProvider<GenerateReproducibleBuildVariablesFileTask> =
  project.tasks.register<GenerateReproducibleBuildVariablesFileTask>(
    "generateReproducibleBuildVariables$capitalizedBuildTypeName"
  ) {
    val outputFile =
      project.layout.buildDirectory.map {
        it.dir("reproducible-build-variables")
          .dir(buildTypeName)
          .file("reproducible-build-variables.json")
      }

    reproducibleBuildVariablesFile.set(outputFile)
  }

private fun Project.configureResourcesDependency(
  capitalizedBuildTypeName: String,
  generateVariablesTask: TaskProvider<GenerateReproducibleBuildVariablesFileTask>,
  buildTypeName: String,
) {
  project.tasks.withName("generate${capitalizedBuildTypeName}Resources")
    .configureEach {
      dependsOn(generateVariablesTask)
    }

  extensions.configure(AppExtension::class.java) {
    sourceSets.withName(buildTypeName).configureEach {
      val buildVariablesDirectory =
        generateVariablesTask.map {
          it.reproducibleBuildVariablesFile.get().asFile.parentFile
        }

      resources.srcDir(buildVariablesDirectory)
    }
  }
}
