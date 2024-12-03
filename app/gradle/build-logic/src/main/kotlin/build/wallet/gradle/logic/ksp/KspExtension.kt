package build.wallet.gradle.logic.ksp

import org.gradle.api.artifacts.Dependency
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

/**
 * Build logic extensions for configuring [KspPlugin].
 */
open class KspExtension
  @Inject
  constructor(objects: ObjectFactory) {
    internal val processors = objects.listProperty(Dependency::class.java)
    internal val targets = objects.property<Targets>()
      // All targets (if available) are configured by default.
      .convention(Targets(android = true, ios = true, jvm = true))

    /**
     * Specifies what KSP targets (if available) should be configured.
     */
    internal data class Targets(
      val android: Boolean,
      val ios: Boolean,
      val jvm: Boolean,
    )

    /**
     * Defines what KSP processors should be added to this KSP configuration.
     */
    fun processors(vararg processor: Dependency) {
      processors.set(processor.toList())
    }

    /**
     * Optional. Explicitly specifies what KSP targets should be configured. By default,
     * all available targets are configured.
     */
    fun targets(
      android: Boolean = false,
      ios: Boolean = false,
      jvm: Boolean = false,
    ) {
      require(android || ios || jvm) { "At least one KSP target must be specified" }
      targets.set(Targets(android, ios, jvm))
    }
  }
