package build.wallet.f8e.client.plugins

import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.OfflineOperationException
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.util.pipeline.*

/**
 * A Ktor Client Plugin to fail requests targeting [F8eEnvironment.ForceOffline].
 */
val ForceOfflinePlugin = createClientPlugin("force-offline-plugin") {
  @Suppress("LocalVariableName")
  val BeforeSend = PipelinePhase("BeforeSend")
  client.sendPipeline.insertPhaseBefore(HttpSendPipeline.Engine, BeforeSend)
  client.sendPipeline.intercept(BeforeSend) { _ ->
    val f8eEnvironment = context.attributes[F8eEnvironmentAttribute]
    if (f8eEnvironment == F8eEnvironment.ForceOffline) {
      throw OfflineOperationException
    }
    proceed()
  }
}
