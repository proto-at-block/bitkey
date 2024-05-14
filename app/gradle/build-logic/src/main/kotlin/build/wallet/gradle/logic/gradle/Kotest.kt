package build.wallet.gradle.logic.gradle

import org.gradle.api.tasks.testing.Test

/**
 * Configures the Gradle [Test] task to propagate Kotest-relevant system properties.
 *
 * This extension function ensures that Kotest system properties are copied from the Gradle JVM to
 * the JVM test executors running the Kotest tests. This is needed for allowing Kotest-specific
 * configurations to be recognized during test execution.
 *
 * Usage:
 * ```kotlin
 * tasks.withType<Test>().propagateKotestSystemProperties()
 * ```
 */
internal fun Test.propagateKotestSystemProperties() {
  kotestSystemProperties.forEach { name ->
    System.getProperty(name)?.let { value ->
      systemProperty(name, value)
    }
  }
}

/**
 * List of Kotest system properties that we set in the Gradle JVM and want to propagate to the JVM
 * test executors running the Kotest tests.
 *
 * All available Kotest system properties can be found here: https://kotest.io/docs/framework/framework-config-props.html#kotestenginepropertieskt.
 */
private val kotestSystemProperties =
  listOf(
    // To enable Kotest tags feature with Gradle. Without propagating this property, Kotest won't be
    // able to pick up tags passed to Gradle.
    // See https://kotest.io/docs/framework/tags.html#gradle.
    "kotest.tags"
  )
