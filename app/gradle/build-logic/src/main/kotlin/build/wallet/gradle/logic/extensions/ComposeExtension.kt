package build.wallet.gradle.logic.extensions

import build.wallet.gradle.logic.gradle.debugImplementation
import build.wallet.gradle.logic.gradle.implementation
import build.wallet.gradle.logic.gradle.libs
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

/**
 * DSL for Compose/Compose UI features.
 *
 * Default configuration:
 * ```kotlin
 * buildLogic {
 *   compose {
 *     composeRuntime()
 *     composeUi()
 *   }
 * }
 * ```
 */
open class ComposeExtension
  @Inject
  constructor(
    objects: ObjectFactory,
    private val project: Project,
  ) {
    private val enableComposeRuntime = objects.property<Boolean>().convention(false)
    private val enableComposeUi = objects.property<Boolean>().convention(false)

    /**
     * Enables Compose Runtime for the project.
     *
     * For only, only works with Android projects.
     */
    fun composeRuntime() {
      enableComposeRuntime.set(true)
      enableComposeRuntime.disallowChanges()

      project.run {
        android {
          buildFeatures {
            compose = true
          }

          composeOptions {
            kotlinCompilerExtensionVersion = libs.versions.android.compose.ui.compiler.get()
          }
        }

        dependencies {
          implementation(libs.kmp.compose.runtime)
        }
      }
    }

    /**
     * Enables Compose Runtime for the project and adds common Compose UI dependencies.
     *
     * Enables Compose UI tooling and previews.
     */
    fun composeUi() {
      // Compose UI requires Compose runtime.
      if (!enableComposeRuntime.get()) {
        composeRuntime()
      }

      enableComposeUi.set(true)
      enableComposeUi.disallowChanges()

      project.run {
        dependencies {
          implementation(libs.android.compose.ui.core)
          implementation(libs.android.compose.ui.util)
          // Primarily needed for screens to implement back navigation using BackHandler.
          implementation(libs.android.compose.ui.activity)
          implementation(libs.android.compose.ui.foundation)

          debugImplementation(libs.android.compose.ui.tooling)
          // Using main variant so that release builds can compile private `@Preview` functions.
          implementation(libs.android.compose.ui.tooling.preview)
        }
      }
    }
  }

private fun Project.android(configure: Action<CommonExtension<*, *, *, *, *>>): Unit =
  extensions.configure("android", configure)
