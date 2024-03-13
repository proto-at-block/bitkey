package build.wallet.gradle.logic

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class GenerateStateMachineDiagramTask : DefaultTask() {
  @get:InputDirectory
  abstract val directory: DirectoryProperty

  @get:Input
  @get:Optional
  abstract val machineName: Property<String>

  @get:Input
  abstract val excludes: SetProperty<String>

  @get:OutputFile
  @get:Optional
  abstract val outputFile: RegularFileProperty

  @get:Inject
  abstract val execOperations: ExecOperations

  init {
    group = "help"
    description = "Generates a mermaid diagram of the state machines in the project"
  }

  @TaskAction
  fun generate() {
    val stateMachineRegex = "class (\\w*?)StateMachineImpl(\\(.*?\\))".toRegex(
      setOf(
        RegexOption.MULTILINE,
        RegexOption.DOT_MATCHES_ALL
      )
    )
    val dependencyMachineRegex = "[^:]*?:\\s*(\\w*?)StateMachine".toRegex(
      setOf(
        RegexOption.MULTILINE,
        RegexOption.DOT_MATCHES_ALL
      )
    )
    val excludedMachineNames = excludes.get()
    val machines = mutableMapOf<String, MutableSet<String>>()
    directory.get().asFile.walk().forEach { file ->
      if (file.extension != "kt") {
        return@forEach
      }

      stateMachineRegex.findAll(file.readText()).forEach { match ->
        val name = match.groupValues[1]
        if (name !in excludedMachineNames) {
          val dependencies = match.groupValues.elementAtOrNull(2)?.let { params ->
            dependencyMachineRegex.findAll(params).mapNotNull { dependencyMatch ->
              dependencyMatch.groupValues[1].takeIf { it !in excludedMachineNames }
            }
          } ?: emptySequence()
          machines.getOrPut(name) { mutableSetOf() }.addAll(dependencies)
        }
      }
    }

    fun Map<String, Set<String>>.fromNode(node: String): Set<String> {
      return this[node]?.let { dependencies ->
        setOf(node) + dependencies.flatMap { fromNode(it) }
      } ?: setOf(node)
    }

    val machinesToShow = machineName.orNull?.let { rootName ->
      machines.fromNode(rootName)
    } ?: machines.keys

    val mermaid = buildString {
      appendLine("graph LR")

      machinesToShow.sorted().forEach { name ->
        val dependencies = machines[name] ?: emptySet()
        dependencies.forEach { dependency ->
          if (dependency in machinesToShow) {
            appendLine("  $name --> $dependency")
          }
        }
      }
    }

    println("=========")
    println("Found ${machines.size} machines with ${machines.values.flatten().toSet().size} dependencies")
    println(
      machines.entries.joinToString("\n") { (name, dependencies) ->
        "<$name>(${dependencies.joinToString()})"
      }
    )
    println("=========")
    println(mermaid)
    println("=========")

    // Save graph to file
    outputFile.orNull?.asFile?.let { file ->
      file.writeText(mermaid)
      println("The graph code above has been saved to ${file.absolutePath}")
    }

    if (Os.isFamily(Os.FAMILY_MAC)) {
      // Copy graph to clipboard
      execOperations.exec {
        commandLine("pbcopy")
        standardInput = mermaid.byteInputStream()
      }
      println("The graph code above has been copied to your clipboard. Open https://mermaid.live/edit to visualize it.")
    }
  }
}
