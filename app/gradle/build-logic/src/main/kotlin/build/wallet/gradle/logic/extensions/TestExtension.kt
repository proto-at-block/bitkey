package build.wallet.gradle.logic.extensions

import app.cash.paparazzi.gradle.PaparazziPlugin
import build.wallet.gradle.logic.gradle.apply
import build.wallet.gradle.logic.gradle.configureJvmTestLogging
import build.wallet.gradle.logic.gradle.libs
import build.wallet.gradle.logic.gradle.propagateKotestSystemProperties
import build.wallet.gradle.logic.gradle.testImplementation
import org.gradle.api.Project
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*
import javax.inject.Inject

/**
 * DSL for testing features (unit testing, assertions).
 *
 * Example
 * ```kotlin
 * buildLogic {
 *   test {
 *     unitTests()
 *     snapshotTests()
 *   }
 * }
 * ```
 */
open class TestExtension
  @Inject
  constructor(
    objects: ObjectFactory,
    private val project: Project,
  ) {
    private val enableUnitTests = objects.property<Boolean>().convention(false)

    fun unitTests() =
      project.run {
        enableUnitTests.set(true)
        enableUnitTests.disallowChanges()

        tasks.withType<Test>().configureEach {
          useJUnitPlatform()
          configureJvmTestLogging()
          propagateKotestSystemProperties()
        }

        dependencies {
          testImplementation(libs.jvm.test.kotest.junit)
          testImplementation(libs.kmp.kotlin.reflection)
          testImplementation(libs.kmp.test.kotest.assertions)
          testImplementation(libs.kmp.test.kotest.framework.engine)
        }
      }

    /**
     * Applies Paparazzi plugin and adds necessary dependencies for writing
     * and running Snapshot tests.
     */
    fun snapshotTests() =
      project.run {
        if (!enableUnitTests.get()) {
          unitTests()
        }
        pluginManager.apply<PaparazziPlugin>()

        dependencies {
          testImplementation(project(":android:kotest-paparazzi-public"))
        }

        plugins.withId("app.cash.paparazzi") {
          // Defer until afterEvaluate so that testImplementation is created by Android plugin.
          afterEvaluate {
            dependencies.constraints {
              add("testImplementation", "com.google.guava:guava") {
                attributes {
                  attribute(
                    TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                    objects.named(TargetJvmEnvironment::class, TargetJvmEnvironment.STANDARD_JVM)
                  )
                }
                because(
                  "LayoutLib and sdk-common depend on Guava's -jre published variant." +
                    "See https://github.com/cashapp/paparazzi/issues/906."
                )
              }
            }
          }
        }
      }
  }
