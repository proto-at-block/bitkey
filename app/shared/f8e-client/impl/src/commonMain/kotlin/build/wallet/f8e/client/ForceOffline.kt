package build.wallet.f8e.client

import build.wallet.f8e.F8eEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin

/**
 * Blocks HTTP calls to F8e when the environment is set to [F8eEnvironment.ForceOffline].
 */
internal fun HttpClient.interceptWhenOffline(f8eEnvironment: F8eEnvironment) =
  apply {
    plugin(HttpSend).intercept { request ->
      if (f8eEnvironment == F8eEnvironment.ForceOffline) {
        throw OfflineOperationException
      }
      execute(request)
    }
  }
