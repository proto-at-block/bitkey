package build.wallet.gradle.logic.ksp

import build.wallet.gradle.logic.KotlinMultiplatformPlugin
import build.wallet.gradle.logic.android
import build.wallet.gradle.logic.gradle.kotlin
import build.wallet.gradle.logic.gradle.requirePlugin
import build.wallet.gradle.logic.gradle.sourceSets
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Configures platform-specific KSP code generation. Adds generated KSP code directories to
 * sources (for each added KSP target per available KMP target).
 *
 * KSP processors can be configured using `buildLogic` DLS:
 *
 * ```kotlin
 * plugins {
 *   id("build.wallet.kmp")
 *   id("build.wallet.ksp")
 * }
 *
 * kotlin {
 *   allTargets()
 * }
 *
 * buildLogic {
 *   ksp {
 *     // Specify KSP processors
 *     processors(
 *       project.gradle.myCustomProcessor, // as project dep
 *       libs.externalProcessor.get(), // as lib dep
 *     )
 *
 *     // Optional: specify KSP targets. By default KSP is enabled for all available targets.
 *     targets(ios = true) // In this case, KSP is only enabled for iOS target.
 *   }
 * }
 * ```
 */
internal class KspPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.run {
      pluginManager.requirePlugin(KotlinMultiplatformPlugin.ID)
      pluginManager.apply("com.google.devtools.ksp")

      val extension = extensions.create("kspExtension", KspExtension::class.java)

      // KMP targets are configured after project is evaluated by our KotlinMultiplatformExtension,
      // so we need to add KSP dependencies and generated source directories in `afterEvaluate` block.
      afterEvaluate {
        addProcessors(extension.processors.get())
        addGeneratedDirs()
      }
    }
  }

  /**
   * Adds KSP-generated source directories to the project’s source sets.
   *
   * These generated sources need to be registered in the appropriate source sets (e.g., `jvmMain`,
   * `androidMain`, `iosMain`) so that they are available for compilation.
   */
  private fun Project.addGeneratedDirs() {
    val kspGeneratedDir = "${layout.buildDirectory.get()}/generated/ksp"
    val targets = extensions.getByType(KspExtension::class.java).targets.get()

    kotlin {
      sourceSets {
        if (targets.jvm) {
          findByName("jvmMain")?.apply {
            kotlin.srcDir("$kspGeneratedDir/jvm/jvmMain/kotlin")
          }
        }

        if (targets.android) {
          findByName("androidMain")?.apply {
            // Add appropriate source directory based on Android build type (release or debug)
            project.android {
              sourceSets {
                getByName("debug").apply {
                  kotlin.srcDir("$kspGeneratedDir/android/androidDebug/kotlin")
                }
                getByName("release").apply {
                  kotlin.srcDir("$kspGeneratedDir/android/androidRelease/kotlin")
                }
              }
            }
          }
        }

        if (targets.ios) {
          findByName("iosMain")?.apply {
            kotlin.srcDir("$kspGeneratedDir/ios/iosMain/kotlin")
          }
          findByName("iosArm64Main")?.apply {
            kotlin.srcDir("$kspGeneratedDir/iosArm64/iosArm64Main/kotlin")
          }
          findByName("iosX64Main")?.apply {
            kotlin.srcDir("$kspGeneratedDir/iosX64/iosX64Main/kotlin")
          }
          findByName("iosSimulatorArm64Main")?.apply {
            kotlin.srcDir("$kspGeneratedDir/iosSimulatorArm64/iosSimulatorArm64Main/kotlin")
          }
        }
      }
    }
  }

  /**
   * Adds KSP processors to the relevant targets.
   *
   * Dynamically identifies the available source sets (e.g., `jvmMain`, `androidMain`, `iosMain`)
   * and assigns the corresponding dependencies to them.
   */
  private fun Project.addProcessors(processors: List<Dependency>) {
    val targets = extensions.getByType<KspExtension>().targets.get()
    kotlin {
      val kspTargets = buildList {
        sourceSets {
          if (targets.jvm) {
            if (findByName("jvmMain") != null) {
              add("kspJvm")
            }
          }

          if (targets.android) {
            if (findByName("androidMain") != null) {
              if (configurations.findByName("kspAndroidRelease") != null) {
                add("kspAndroidRelease")
              }
              if (configurations.findByName("kspAndroidDebug") != null) {
                add("kspAndroidDebug")
              }
            }
          }

          if (targets.ios) {
            if (findByName("iosArm64Main") != null) {
              add("kspIosArm64")
            }
            if (findByName("iosSimulatorArm64Main") != null) {
              add("kspIosSimulatorArm64")
            }
            if (findByName("iosX64Main") != null) {
              add("kspIosX64")
            }
          }
        }
      }

      project.dependencies {
        kspTargets.forEach { kspTarget ->
          processors.forEach { processor ->
            add(kspTarget, processor)
          }
        }
      }
    }
  }
}
