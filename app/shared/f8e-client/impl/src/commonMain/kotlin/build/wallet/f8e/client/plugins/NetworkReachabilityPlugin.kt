@file:Suppress("detekt:MatchingDeclarationName")

package build.wallet.f8e.client.plugins

import build.wallet.availability.NetworkConnection
import build.wallet.availability.NetworkReachability.REACHABLE
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.isClientError
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.pipeline.*

class NetworkReachabilityPluginConfig {
  lateinit var networkReachabilityProvider: NetworkReachabilityProvider
}

val NetworkReachabilityPlugin = createClientPlugin(
  "network-reachability",
  ::NetworkReachabilityPluginConfig
) {
  val networkReachabilityProvider = pluginConfig.networkReachabilityProvider
  onResponse { response ->
    val checkReachability = response.request.attributes[CheckReachabilityAttribute]
    if (!checkReachability) {
      return@onResponse
    }
    val f8eEnvironment = response.request.attributes[F8eEnvironmentAttribute]
    val responseStatus = response.status
    networkReachabilityProvider.updateNetworkReachabilityForConnection(
      httpClient = this@createClientPlugin.client,
      reachability =
        when {
          // REACHABLE if success
          responseStatus.isSuccess() -> REACHABLE
          // UNREACHABLE if forbidden (403)
          responseStatus == HttpStatusCode.Forbidden -> UNREACHABLE
          // REACHABLE if client error (other 4xx)
          responseStatus.isClientError -> REACHABLE
          // UNREACHABLE if any other error
          else -> UNREACHABLE
        },
      connection = NetworkConnection.HttpClientNetworkConnection.F8e(f8eEnvironment)
    )
  }
  client.sendPipeline.intercept(HttpSendPipeline.Before) {
    val checkReachability = context.attributes[CheckReachabilityAttribute]
    if (!checkReachability) {
      return@intercept
    }
    val f8eEnvironment = context.attributes[F8eEnvironmentAttribute]
    if (f8eEnvironment == F8eEnvironment.ForceOffline) {
      networkReachabilityProvider.updateNetworkReachabilityForConnection(
        httpClient = this@createClientPlugin.client,
        reachability = UNREACHABLE,
        connection = NetworkConnection.HttpClientNetworkConnection.F8e(f8eEnvironment)
      )
    }
  }

  // Install hook before Receive process to capture exceptions during response phase.
  @Suppress("LocalVariableName")
  val BeforeReceive = PipelinePhase("BeforeReceive")
  client.responsePipeline.insertPhaseBefore(HttpResponsePipeline.Receive, BeforeReceive)
  client.responsePipeline.intercept(BeforeReceive) { container ->
    val checkReachability = context.request.attributes[CheckReachabilityAttribute]
    if (!checkReachability) {
      return@intercept
    }
    val f8eEnvironment = context.request.attributes[F8eEnvironmentAttribute]
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    try {
      proceedWith(container)
    } catch (cause: Throwable) {
      val unwrappedCause = cause.unwrapCancellationException()
      networkReachabilityProvider.updateNetworkReachabilityForConnection(
        httpClient = this@createClientPlugin.client,
        reachability = UNREACHABLE,
        connection = NetworkConnection.HttpClientNetworkConnection.F8e(f8eEnvironment)
      )
      throw unwrappedCause
    }
  }
}
