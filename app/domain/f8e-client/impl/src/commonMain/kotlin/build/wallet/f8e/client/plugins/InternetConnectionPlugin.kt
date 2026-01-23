package build.wallet.f8e.client.plugins

import build.wallet.f8e.client.OfflineOperationException
import build.wallet.platform.connectivity.InternetConnectionChecker
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpSendPipeline
import io.ktor.util.InternalAPI
import io.ktor.util.pipeline.PipelinePhase

/**
 * Configuration for [InternetConnectionPlugin].
 */
class InternetConnectionPluginConfig {
  var internetConnectionChecker: InternetConnectionChecker? = null
}

/**
 * A Ktor Client Plugin that checks for internet connectivity before sending requests.
 *
 * This prevents requests from being sent when the device doesn't have a working
 * internet connection, which would otherwise result in DNS resolution failures
 * (UnknownHostException).
 *
 * On Android, this uses `NET_CAPABILITY_VALIDATED` to ensure the network has been
 * verified by the OS to actually work. This addresses the timing issue where Android
 * reports "connected" before the network is fully ready.
 */
@OptIn(InternalAPI::class)
val InternetConnectionPlugin = createClientPlugin(
  "internet-connection-plugin",
  ::InternetConnectionPluginConfig
) {
  val checker = pluginConfig.internetConnectionChecker
    ?: return@createClientPlugin // No checker provided, skip

  @Suppress("LocalVariableName")
  val BeforeConnect = PipelinePhase("BeforeConnect")
  client.sendPipeline.insertPhaseBefore(HttpSendPipeline.Engine, BeforeConnect)
  client.sendPipeline.intercept(BeforeConnect) {
    if (!checker.isConnected()) {
      throw OfflineOperationException
    }
  }
}
