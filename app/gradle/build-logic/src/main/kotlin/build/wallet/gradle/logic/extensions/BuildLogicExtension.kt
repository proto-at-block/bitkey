package build.wallet.gradle.logic.extensions

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.newInstance

/**
 * Defines a DSL that enables declarative build configuration (specific to this project).
 *
 * ```kotlin
 * buildLogic {
 *   app { }
 *   compose { }
 * }
 * ```
 */
open class BuildLogicExtension(project: Project) {
  private val objects = project.objects

  private val app = objects.newInstance<AppExtension>()
  private val compose = objects.newInstance<ComposeExtension>()
  private val proto = objects.newInstance<ProtoExtension>()
  private val test = objects.newInstance<TestExtension>()

  fun app(action: Action<AppExtension>) {
    action.execute(app)
  }

  fun compose(action: Action<ComposeExtension>) {
    action.execute(compose)
  }

  fun proto(action: Action<ProtoExtension>) {
    action.execute(proto)
  }

  fun test(action: Action<TestExtension>) {
    action.execute(test)
  }
}

fun Project.buildLogic(body: BuildLogicExtension.() -> Unit) {
  extensions.findByType<BuildLogicExtension>()?.let(body) ?: error(
    "Build logic extension is not created."
  )
}
