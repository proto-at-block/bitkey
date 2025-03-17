@file:Suppress("detekt:MatchingDeclarationName")

package build.wallet.f8e.client.plugins

import build.wallet.f8e.debug.NetworkingDebugService
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*

class FailF8eRequestsPluginConfig {
  lateinit var networkingDebugService: NetworkingDebugService
}

/**
 * A Ktor Client Plugin to fail all requests if debug config indicates so.
 */
val FailF8eRequestsPlugin = createClientPlugin(
  "fail-f8e-requests",
  ::FailF8eRequestsPluginConfig
) {
  val networkingDebugService = pluginConfig.networkingDebugService
  onRequest { request, _ ->
    val shouldFailRequests = networkingDebugService.config.value.failF8eRequests
    if (shouldFailRequests) {
      // Throw an exception to simulate a failed request
      throw ForcedF8eRequestFailure
    }
  }
}

/**
 * Exception thrown to simulate a failed f8e request.
 */
private object ForcedF8eRequestFailure : Throwable()
