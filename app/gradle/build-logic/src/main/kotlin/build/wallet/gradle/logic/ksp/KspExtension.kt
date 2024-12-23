package build.wallet.gradle.logic.ksp

import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * Build logic extensions for configuring [KspPlugin].
 */
open class KspExtension
  @Inject
  constructor(objects: ObjectFactory) {
    internal val processors = objects.setProperty(KspProcessorConfig::class.java)

    /**
     * Defines what KSP processors should be added to this KSP configuration.
     */
    fun processors(vararg processor: KspProcessorConfig) {
      processors.addAll(processor.toList())
    }
  }
