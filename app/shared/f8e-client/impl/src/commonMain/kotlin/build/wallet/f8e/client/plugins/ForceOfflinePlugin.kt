package build.wallet.f8e.client.plugins

import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.OfflineOperationException
import io.ktor.client.plugins.api.*
import io.ktor.client.request.HttpSendPipeline
import io.ktor.util.InternalAPI
import io.ktor.util.pipeline.PipelinePhase

/**
 * A Ktor Client Plugin to fail requests targeting [F8eEnvironment.ForceOffline].
 */
@OptIn(InternalAPI::class)
val ForceOfflinePlugin = createClientPlugin("force-offline-plugin") {
  @Suppress("LocalVariableName")
  val BeforeSend = PipelinePhase("BeforeSend")
  client.sendPipeline.insertPhaseBefore(HttpSendPipeline.Engine, BeforeSend)
  client.sendPipeline.intercept(BeforeSend) { request ->
    val f8eEnvironment = context.attributes[F8eEnvironmentAttribute]
    if (f8eEnvironment == F8eEnvironment.ForceOffline) {
      throw OfflineOperationException
    }
  }
}
