@file:Suppress("UnstableApiUsage")
@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

package build.wallet.gradle.logic.extensions

import build.wallet.gradle.logic.AndroidLibPlugin
import build.wallet.gradle.logic.KotlinBasePlugin
import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.gradle.configureJvmTestLogging
import build.wallet.gradle.logic.gradle.kotlin
import build.wallet.gradle.logic.gradle.libs
import build.wallet.gradle.logic.gradle.optimalTargetsForIOS
import build.wallet.gradle.logic.gradle.propagateKotestSystemProperties
import build.wallet.gradle.logic.gradle.sourceSets
import build.wallet.gradle.logic.structure.isFakeModule
import build.wallet.gradle.logic.structure.isImplModule
import build.wallet.gradle.logic.structure.isPublicModule
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.Companion.integrationTest
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.Companion.main
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.Companion.test
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

fun KotlinMultiplatformExtension.allTargets() = targets(android = true, ios = true, jvm = true)

fun KotlinMultiplatformExtension.targets(
  android: Boolean = false,
  ios: Boolean = false,
  jvm: Boolean = false,
) {
  require(android || ios || jvm) { "Expected at lease one target to be configured" }
  applyHierarchyTemplate {
    withSourceSetTree(main, test, integrationTest)
    withCompilations { true }
    common {
      if (jvm || android) {
        group("commonJvm") {
          if (jvm) withJvm()
          if (android) withAndroidTarget()
        }
      }
      if (ios) group("ios") { withIos() }
    }
  }
  val project = metadata().project

  if (android) configureAndroidTarget(project)
  if (jvm) configureJvmTarget()
  if (ios) configureIosTarget(project)

  with(project) {
    configureDependencies()

    if (!isUmbrellaXcFramework) {
      pluginManager.apply<KotlinBasePlugin>()
      configureTests()
    }
  }
}

/**
 * Configure JVM target.
 */
private fun KotlinMultiplatformExtension.configureJvmTarget() {
  jvm {
    val integrationTest by compilations.creating {
      val main by this@jvm.compilations.getting
      associateWith(main)

      defaultSourceSet {
        dependencies {
          implementation(kotlin("test-junit"))
        }
      }
    }

    testRuns.create("integrationTest") {
      // This assigns this test run with the `integrationTest` compilation (by default it's assigned to the `test` compilation)
      setExecutionSourceFrom(integrationTest)

      executionTask.configure {
        // Set a tight timeout for running integration tests to hopefully catch any hanging tests.
        // This timeout is applied per module only guards the test execution itself, not the compilation.
        // The canceled task will show as canceled in the build scan.
        timeout.set(10.minutes.toJavaDuration())

        // Here we give our custom `KotlinJvmTest` the name of its assigned compilation.
        // Note: It might seem like you can just do `setCompilationName(integrationTest.name)` and even IntelliJ will agree.
        //   But then the compilation fails that `setCompilationName` doesn't exist.
        //   I think it's because IDEA considers our KotlinJvmTest for resolution, but compilation then runs againts the original one.
        javaClass.getMethod("setCompilationName", String::class.java)
          .invoke(this, integrationTest.name)

        // Run tests in parallel by default, and allow setting the property
        // -Pbitkey.integrationTest.maxParallelForks=1 to override it
        maxParallelForks =
          (project.findProperty("bitkey.integrationTest.maxParallelForks") as? String)?.toInt()
            ?: (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
      }
    }
  }

  sourceSets {
    jvmTest {
      project.configureJUnit(sourceSet = this)
    }

    jvmIntegrationTest {
      project.configureJUnit(sourceSet = this)
    }
  }
}

private fun Project.configureJUnit(sourceSet: KotlinSourceSet) {
  sourceSet.apply {
    tasks.withType<Test>().configureEach {
      configureJvmTestLogging()
      useJUnitPlatform()
      // Fail any test that takes longer than this timeout duration to run.
      // This is to safeguard against very slow tests or tests that are hanging.
      systemProperty(
        "kotest.framework.timeout",
        // TODO: ideally we would use different timeouts for different test types
        //       but our current setup doesn't allow for such flexibility.
        //       Kotest system property `kotest.framework.timeout` is global and applies to all tests.
        5.minutes.inWholeMilliseconds
      )
      propagateKotestSystemProperties()
    }

    dependencies {
      implementation(libs.jvm.test.kotest.junit)
    }
  }
}

/**
 * Configure Android target.
 */
private fun KotlinMultiplatformExtension.configureAndroidTarget(project: Project) {
  project.pluginManager.apply<AndroidLibPlugin>()

  androidTarget()

  sourceSets {
    androidUnitTest {
      project.configureJUnit(sourceSet = this)
    }
  }
}

/**
 * Configure iOS Native target.
 */
private fun KotlinMultiplatformExtension.configureIosTarget(project: Project) {
  val targets = optimalTargetsForIOS(project)

  sourceSets {
    targets.forEach { target ->
      target.linkSqlite()
    }
  }

  // Disable K/N tests for :bugsnag modules due to a Bugsnag linking issue.
  // TODO(W-4864): re-enable tests in :bugsnag modules.
  val isBugsnagModule = project.name.startsWith("bugsnag")
  if (!isBugsnagModule) {
    project.tasks.register("iosTest") {
      description = "Executes Kotlin/Native unit tests for all available targets."
      group = "Verification"

      // For running tests on an arm64 compatible host.
      project.tasks.findByName("iosX64Test")?.let {
        dependsOn(it)
      }

      // For running tests on host that are arm64 compatible.
      // For running tests on a non-arm64 compatible host.
      project.tasks.findByName("iosSimulatorArm64Test")?.let {
        dependsOn(it)
      }
    }
  }
}

/**
 * Manually linking Sqlite3 binaries.
 *
 * https://github.com/touchlab/SQLiter/issues/88
 * https://github.com/touchlab/SQLiter/issues/77
 */
private fun KotlinNativeTarget.linkSqlite() {
  binaries.configureEach {
    // App builds use SQLCipher
    if (outputKind == NativeOutputKind.TEST) {
      linkerOpts += "-lsqlite3"
    }
  }
}

private fun Project.configureDependencies() {
  kotlin {
    sourceSets {
      commonMain.apply {
        dependencies {
          api(libs.kmp.kotlin.coroutines)

          if (isFakeModule()) {
            api(libs.kmp.test.kotlin.coroutines)
            api(libs.kmp.test.turbine)
          }
        }
      }
    }
  }
}

private fun Project.configureTests() {
  val shouldConfigureTests = isPublicModule() || isImplModule()
  // Only apply tests in :public and :impl modules (skip :fake and :testing)
  if (!shouldConfigureTests) return

  // Apply Kotest Multiplatform plugin to be able to run tests targeting iOS.
  pluginManager.apply(libs.plugins.kotest.kmp)

  kotlin {
    sourceSets {
      val commonTest by getting
      listOf<KotlinSourceSet>(commonTest, commonIntegrationTest).forEach { testSourceSet ->
        testSourceSet.apply {
          dependencies {
            implementation(libs.kmp.kotlin.reflection)
            implementation(libs.kmp.test.kotest.assertions)
            implementation(libs.kmp.test.kotest.framework.api)
            implementation(libs.kmp.test.kotest.framework.engine)
            implementation(libs.kmp.test.kotest.property)
            implementation(libs.kmp.test.kotlin.coroutines)
            implementation(libs.kmp.test.turbine)
          }
        }
      }
    }
  }
}

internal val Project.isUmbrellaXcFramework: Boolean
  get() = name == "xc-framework"
