@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

package build.wallet.gradle.logic.di

import build.wallet.gradle.logic.gradle.kotlin
import build.wallet.gradle.logic.gradle.libs
import build.wallet.gradle.logic.gradle.sourceSets
import build.wallet.gradle.logic.ksp.KspExtension
import build.wallet.gradle.logic.ksp.KspPlugin
import build.wallet.gradle.logic.ksp.KspProcessorConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

/**
 * Sets up Dependency Injection (DI) code generation for KMP.
 *
 * Uses KSP to generate the DI graph using `kotlin-inject` and `kotlin-inject-anvil` libraries.
 *
 * Note that all KMP targets are expected to be configured:
 * ```kotlin
 * kotlin {
 *   allTargets()
 * }
 * ```
 *
 * See [KspPlugin].
 *
 * See docs:
 * - https://github.com/evant/kotlin-inject/blob/main/docs/multiplatform.md
 * - https://github.com/amzn/kotlin-inject-anvil/blob/main/README.md
 */
internal class DiPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.run {
      pluginManager.apply(KspPlugin::class)

      kotlin {
        sourceSets {
          commonMain {
            dependencies {
              implementation(libs.kmp.kotlin.inject.runtime)
              implementation(libs.kmp.kotlin.inject.anvil.runtime)
              api(project(":shared:di-scopes-public"))
            }
          }
        }
      }

      val ksp = extensions.getByType<KspExtension>()
      ksp.processors(
        KspProcessorConfig(
          deps = listOf(
            libs.kmp.kotlin.inject.compiler.get(),
            libs.kmp.kotlin.inject.anvil.compiler.get(),
            dependencies.project(":gradle:di-codegen")
          ),
          android = true,
          jvm = true,
          ios = true
        )
      )
    }
  }
}
