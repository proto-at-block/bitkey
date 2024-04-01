package build.wallet.gradle.logic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util.targets

/**
 * Plugin that automatically adds opt-ins for experimental declarations based on detected registered dependencies.
 * Opt-ins cannot be added globally because the compiler raises a warning if the opt-in annotation is not accessible from the compiled code.
 *
 * The plugin can detect only direct dependencies not transitive dependencies.
 * This limitation can cause some opt-ins to not be registered even though their annotation is on the compile class path.
 * The workarounds are:
 *  - add the transitive dependency explicitly - by putting it in the list of module dependencies
 *  - register the source of the transitive dependency as another provider of the opt-in
 */
internal class AutomaticKotlinOptInPlugin : Plugin<Project> {
  // [OptIn Annotation fq path] to ([dependency id (group:name)] or [list of dependency ids])
  private val optIns: List<Pair<String, Any>> = listOf(
    "kotlinx.coroutines.ExperimentalCoroutinesApi" to "org.jetbrains.kotlinx:kotlinx-coroutines-core",
    "kotlinx.coroutines.DelicateCoroutinesApi" to "org.jetbrains.kotlinx:kotlinx-coroutines-core",
    "kotlinx.serialization.ExperimentalSerializationApi" to listOf(
      "org.jetbrains.kotlinx:kotlinx-serialization-core",
      "org.jetbrains.kotlinx:kotlinx-serialization-json"
    ),
    "androidx.compose.material.ExperimentalMaterialApi" to "androidx.compose.material:material",
    "androidx.compose.material3.ExperimentalMaterial3Api" to "androidx.compose.material3:material3",
    "androidx.compose.foundation.ExperimentalFoundationApi" to listOf(
      "androidx.compose.foundation:foundation",
      "androidx.compose.material:material"
    ),
    "androidx.compose.animation.ExperimentalAnimationApi" to listOf(
      "androidx.compose.animation:animation",
      "androidx.compose.foundation:foundation",
      "androidx.compose.material:material"
    ),
    "androidx.compose.ui.ExperimentalComposeUiApi" to "androidx.compose.ui:ui",
    "io.kotest.common.ExperimentalKotest" to listOf(
      "io.kotest:kotest-common",
      "io.kotest:kotest-framework-api"
    ),
    "com.russhwolf.settings.ExperimentalSettingsApi" to "com.russhwolf:multiplatform-settings",
    "com.russhwolf.settings.ExperimentalSettingsImplementation" to "com.russhwolf:multiplatform-settings",
    "co.touchlab.kermit.ExperimentalKermitApi" to "co.touchlab:kermit"
  )

  private val commonOptIns = listOf(
    "kotlin.time.ExperimentalTime",
    "kotlin.ExperimentalStdlibApi",
    "kotlin.io.encoding.ExperimentalEncodingApi",
    "kotlin.experimental.ExperimentalObjCRefinement",
    "kotlin.ExperimentalUnsignedTypes",
    "kotlin.contracts.ExperimentalContracts"
  )

  private val nativeOptIns = listOf(
    "kotlinx.cinterop.BetaInteropApi",
    "kotlinx.cinterop.ExperimentalForeignApi"
  )

  private val optInsByDependencyId =
    optIns.flatMap { (optIn, dependencies) ->
      when (dependencies) {
        is String -> listOf(optIn to dependencies)
        is Collection<*> -> dependencies.map { optIn to it as String }
        else -> error("Unsupported dependency declaration type: $dependencies")
      }
    }
      .groupBy { it.second }
      .mapValues { (_, value) -> value.map { it.first } }

  override fun apply(target: Project) {
    with(target) {
      plugins.withId("org.jetbrains.kotlin.multiplatform") {
        configureForKmp()
      }

      plugins.withId("org.jetbrains.kotlin.android") {
        configureForAndroid()
      }
    }
  }

  private fun Project.configureForKmp() {
    configureEachMultiplatformCompilation {
      registerOptInsForExperimentalApis()

      optInForExpectActualClasses()
    }
  }

  private fun Project.configureForAndroid() {
    configureEachAndroidKotlinCompilation {
      registerOptInsForExperimentalApis()
    }
  }

  private fun KotlinCompilation<*>.registerOptInsForExperimentalApis() {
    val newOptIns = getOptIns()

    compileTaskProvider.configure {
      compilerOptions.optIn.addAll(newOptIns)
    }
  }

  private fun KotlinCompilation<*>.optInForExpectActualClasses() {
    compileTaskProvider.configure {
      compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
    }
  }

  private fun KotlinCompilation<*>.getOptIns(): List<String> {
    val nativeOptIns = nativeOptIns.takeIf { target.platformType == KotlinPlatformType.native }
      ?: emptyList()

    val dependencyOptIns = allCompileDependencies.flatMap {
      optInsByDependencyId[it.id] ?: emptyList()
    }

    return (commonOptIns + nativeOptIns + dependencyOptIns).distinct()
  }

  private val KotlinCompilation<*>.allCompileDependencies: DependencySet
    get() = project.configurations.getByName(compileDependencyConfigurationName).allDependencies

  private val Dependency.id: String
    get() = "$group:$name"

  private fun Project.configureEachMultiplatformCompilation(
    action: KotlinCompilation<*>.() -> Unit,
  ) {
    // Must run after KMP plugin configures the compilations correctly, but before it locks compiler options
    gradle.taskGraph.whenReady {
      extensions.configure(KotlinMultiplatformExtension::class.java) {
        targets.configureEach {
          compilations.configureEach {
            action()
          }
        }
      }
    }
  }

  private fun Project.configureEachAndroidKotlinCompilation(
    action: KotlinCompilation<*>.() -> Unit,
  ) {
    // Must run after Kotlin plugin configures the compilations correctly, but before it locks compiler options
    gradle.taskGraph.whenReady {
      extensions.configure(KotlinAndroidProjectExtension::class.java) {
        targets.forEach {
          it.compilations.configureEach {
            action()
          }
        }
      }
    }
  }
}
