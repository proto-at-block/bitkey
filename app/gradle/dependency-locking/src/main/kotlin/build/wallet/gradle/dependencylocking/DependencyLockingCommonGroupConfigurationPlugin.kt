package build.wallet.gradle.dependencylocking

import build.wallet.gradle.dependencylocking.extension.DependencyLockingExtension
import build.wallet.gradle.dependencylocking.extension.commonDependencyLockingGroups
import build.wallet.gradle.dependencylocking.util.ifMatches
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Plugin to set up a common configuration for our custom Gradle dependency locking.
 */
class DependencyLockingCommonGroupConfigurationPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.applyCommonLockingConfiguration()
  }

  private fun Project.applyCommonLockingConfiguration() {
    extensions.configure<DependencyLockingExtension> {
      configureGradleBuildscriptConfigurations()
      configureBuildConfigurations()
    }
  }

  private fun DependencyLockingExtension.configureGradleBuildscriptConfigurations() {
    project.buildscript.configurations.configureEach {
      ifMatches { nameIs("classpath") } then {
        dependencyLockingGroup.set(commonDependencyLockingGroups.gradlePlugins)
      }
    }
  }

  private fun DependencyLockingExtension.configureBuildConfigurations() {
    project.configurations.configureEach {
      ifMatches {
        nameContains("compilerPlugin")
        nameIs(
          "kotlinBuildToolsApiClasspath",
          "kotlinCompilerClasspath",
          "kotlinKlibCommonizerClasspath",
          "kotlin-extension"
        )
      } then {
        dependencyLockingGroup.set(commonDependencyLockingGroups.kotlinCompiler)
      }

      ifMatches {
        nameEndsWith(
          "runtimeClasspath",
          "compileClasspath",
          "compilationApi",
          "Klibraries",
          "metadata",
          "frameworkExport",
          "CInterop",
          "kspClasspath",
          "ProcessorClasspath",
          "layoutlibRuntime",
          "DependenciesMetadata"
        )
        nameEndsWith("BenchmarkGenerateCP")
        nameContains("PluginClasspath")
        nameIs(
          "androidApis",
          "protoPath",
          "protoProjectDependenciesJvm",
          "protoSource",
          "nativePlatform",
          "androidTestUtil"
        )
      } then {
        dependencyLockingGroup.set(commonDependencyLockingGroups.buildClasspath)
      }

      ifMatches {
        nameContains("annotationProcessor")
        nameIs("coreLibraryDesugaring", "androidJdkImage")
      } then {
        dependencyLockingGroup.set(commonDependencyLockingGroups.buildToolchain)
      }

      ifMatches {
        nameContains("kotlinScript")
      } then {
        dependencyLockingGroup.set(commonDependencyLockingGroups.kotlinScript)
      }

      ifMatches {
        // Linter does not affect the build
        nameContains("lint")
        nameIs("detekt", "detektPlugins")
      } then {
        isLocked.set(false)
      }

      ifMatches {
        nameContains("_internal-unified-test")
        nameIs("kotlinNativeBundleConfiguration")
        nameEndsWith(
          "DependencySources",
          "COMPOSE_SKIKO_JS_WASM_RUNTIME",
          "layoutlibResources"
        )
      } then {
        isLocked.set(false)
      }
    }
  }
}
