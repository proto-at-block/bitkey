package build.wallet.gradle.logic.gradle

import org.gradle.api.plugins.PluginManager
import org.gradle.plugin.use.PluginDependency

/**
 * Kotlin-friendly way to apply a plugin using type parameter:
 *
 * ```kotlin
 * pluginManager.apply<MyPlugin>()
 * ```
 */
internal inline fun <reified T> PluginManager.apply(): Unit = apply(T::class.java)

/**
 * Convenience method for applying plugins, along with [PluginDependency].
 */
internal fun PluginManager.apply(plugin: Any) {
  val pluginId =
    when (val pluginDependency = plugin.unwrappedProvider()) {
      is PluginDependency -> pluginDependency.pluginId
      is String -> pluginDependency
      else -> error("Unsupported plugin type: $pluginDependency")
    }

  apply(pluginId)
}

/**
 * Throws if plugin [id] is not applied.
 */
internal fun PluginManager.requirePlugin(id: String) {
  require(hasPlugin(id)) { "Expected '$id' plugin to be applied." }
}
