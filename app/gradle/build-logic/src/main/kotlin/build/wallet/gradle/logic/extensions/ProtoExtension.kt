package build.wallet.gradle.logic.extensions

import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.gradle.kotlin
import build.wallet.gradle.logic.gradle.libs
import com.squareup.wire.gradle.WireExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import javax.inject.Inject

/**
 * DSL for configuring Kotlin proto generation using Wire Gradle plugin.
 */
open class ProtoExtension
  @Inject
  constructor(
    private val project: Project,
  ) {
    fun wire(configuration: WireExtension.() -> Unit) =
      project.run {
        pluginManager.apply(libs.plugins.wire)

        extensions.getByType<WireExtension>().apply {
          // Apply Wire Gradle plugin configuration.
          configuration()

          kotlin {
            // We don't use Java, and so don't care about Kotlin - Java interop.
            javaInterop = false
          }
        }
      }
  }
