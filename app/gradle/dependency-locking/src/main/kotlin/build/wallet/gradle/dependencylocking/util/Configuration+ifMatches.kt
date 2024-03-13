package build.wallet.gradle.dependencylocking.util

import org.gradle.api.artifacts.Configuration

fun Configuration.ifMatches(
  predicateBuilderBody: ConfigurationActionPredicateBuilder.() -> Unit,
): ConfigurationActionBuilder {
  val builder = ConfigurationActionPredicateBuilder(this)

  builder.predicateBuilderBody()

  return if (builder.result) ConfigurationActionBuilder.Success(this) else ConfigurationActionBuilder.Failure
}

class ConfigurationActionPredicateBuilder(configuration: Configuration) {
  var result: Boolean = false
    private set

  private val configurationName = configuration.name

  fun nameContains(vararg substrings: String) {
    result = result || substrings.any { configurationName.contains(it, ignoreCase = true) }
  }

  fun nameEndsWith(vararg suffixes: String) {
    result = result || suffixes.any { configurationName.endsWith(it, ignoreCase = true) }
  }

  fun nameIs(vararg strings: String) {
    result = result || strings.any { configurationName.equals(it, ignoreCase = true) }
  }
}

sealed interface ConfigurationActionBuilder {
  infix fun then(action: Configuration.() -> Unit)

  data class Success(private val configuration: Configuration) : ConfigurationActionBuilder {
    override fun then(action: Configuration.() -> Unit) {
      configuration.action()
    }
  }

  object Failure : ConfigurationActionBuilder {
    override fun then(action: Configuration.() -> Unit) {
    }
  }
}
