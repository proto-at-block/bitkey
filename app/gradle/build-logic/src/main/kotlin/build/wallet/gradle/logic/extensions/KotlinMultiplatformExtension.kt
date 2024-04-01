@file:Suppress("UnstableApiUsage")
@file:OptIn(ExternalVariantApi::class, ExperimentalKotlinGradlePluginApi::class)

package build.wallet.gradle.logic.extensions

import build.wallet.gradle.logic.AndroidLibPlugin
import build.wallet.gradle.logic.KotlinBasePlugin
import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.gradle.kotlin
import build.wallet.gradle.logic.gradle.libs
import build.wallet.gradle.logic.gradle.optimalTargetsForIOS
import build.wallet.gradle.logic.gradle.propagateKotestSystemProperties
import build.wallet.gradle.logic.gradle.sourceSets
import build.wallet.gradle.logic.structure.isFakeModule
import build.wallet.gradle.logic.structure.isImplModule
import build.wallet.gradle.logic.structure.isPublicModule
import com.adarshr.gradle.testlogger.TestLoggerExtension
import com.adarshr.gradle.testlogger.TestLoggerPlugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.kpm.external.ExternalVariantApi
import org.jetbrains.kotlin.gradle.kpm.external.project
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.Companion.integrationTest
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.Companion.main
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree.Companion.test
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind

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

  if (android) configureAndroidTarget()
  if (jvm) configureJvmTarget()
  if (ios) configureIosTarget()

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
      val main by compilations.getting
      associateWith(main)

      defaultSourceSet {
        dependencies {
          implementation(kotlin("test-junit"))
        }
      }

      project.generateJvmIntegrationTestRunConfiguration()
    }

    testRuns.create("integrationTest") {
      // This assigns this test run with the `integrationTest` compilation (by default it's assigned to the `test` compilation)
      setExecutionSourceFrom(integrationTest)

      executionTask.configure {
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

/**
 * Generate runner configuration for JVM Integration tests manually, until Kotest plugin
 * supports running tests from custom test compilations: https://github.com/kotest/kotest-intellij-plugin/issues/266.
 */
private fun Project.generateJvmIntegrationTestRunConfiguration() {
  val runConfigurationsDir = rootDir.resolve(".idea/runConfigurations")
  runConfigurationsDir.mkdirs()

  val integrationTestDir = projectDir.resolve("src/jvmIntegrationTest/kotlin")

  if (integrationTestDir.exists()) {
    // Generate run configuration for each integration test file, if any:
    val testFiles =
      integrationTestDir.walk().filter {
        it.isFile && (it.name.endsWith("Tests.kt") || it.name.endsWith("Test.kt"))
      }
    testFiles.forEach { file ->
      val configName = file.nameWithoutExtension
      val fullyQualifiedName =
        file.toRelativeString(integrationTestDir).replace("/", ".").removeSuffix(".kt")

      val runConfigurationXml =
        """
        <component name="ProjectRunConfigurationManager">
          <configuration default="false" name="$configName" type="GradleRunConfiguration" factoryName="Gradle">
            <ExternalSystemSettings>
              <option name="executionName" />
              <option name="externalProjectPath" value="${"$"}PROJECT_DIR$" />
              <option name="externalSystemIdString" value="GRADLE" />
              <option name="scriptParameters" value="" />
              <option name="taskDescriptions">
                <list />
              </option>
              <option name="taskNames">
                <list>
                  <option value="${project.path}:cleanJvmTest" />
                  <option value="${project.path}:jvmIntegrationTest" />
                  <option value="--tests" />
                  <option value="&quot;$fullyQualifiedName&quot;" />
                </list>
              </option>
              <option name="vmOptions" />
            </ExternalSystemSettings>
            <ExternalSystemDebugServerProcess>false</ExternalSystemDebugServerProcess>
            <ExternalSystemReattachDebugProcess>true</ExternalSystemReattachDebugProcess>
            <DebugAllEnabled>false</DebugAllEnabled>
            <ForceTestExec>true</ForceTestExec>
            <method v="2" />
          </configuration>
        </component>
        """.trimIndent()

      val runConfigurationFile = runConfigurationsDir.resolve("$configName.xml")
      runConfigurationFile.writeText(runConfigurationXml)
    }
  }
}

private fun Project.configureJUnit(sourceSet: KotlinSourceSet) {
  project.pluginManager.apply<TestLoggerPlugin>()
  extensions.configure(TestLoggerExtension::class) {
    // Print test logging output only if the test fails
    showStandardStreams = true
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = true
    // Don't show names of passing tests, since we have hundreds
    showPassed = false
  }

  sourceSet.apply {
    tasks.withType<Test>().configureEach {
      useJUnitPlatform()
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
private fun KotlinMultiplatformExtension.configureAndroidTarget() {
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
private fun KotlinMultiplatformExtension.configureIosTarget() {
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
