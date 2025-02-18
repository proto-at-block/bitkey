package build.wallet.f8e.debug

import app.cash.turbine.test
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.plugins.FailF8eRequestsPlugin
import build.wallet.ktor.result.HttpError
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient

class NetworkDebugFunctionalTests : FunSpec({
  test("force fail f8e requests") {
    val app = launchNewApp()
    val f8eNetworkReachabilityService = app.f8eNetworkReachabilityService
    val networkingDebugService = app.networkingDebugService
    val client = HttpClient {
      install(FailF8eRequestsPlugin) {
        this.networkingDebugService = app.networkingDebugService
      }
    }

    // Check that f8e requests are successful by default
    f8eNetworkReachabilityService.checkConnection(client, F8eEnvironment.Local).shouldBeOk()

    // Configure the app to fail all f8e requests
    networkingDebugService.setFailF8eRequests(value = true)
    networkingDebugService.config.test {
      awaitUntil { it.failF8eRequests }
    }

    // Check that f8e requests are failing
    f8eNetworkReachabilityService.checkConnection(client, F8eEnvironment.Local)
      .shouldBeErrOfType<HttpError>()

    // Configure the app to not fail f8e requests
    networkingDebugService.setFailF8eRequests(value = false)
    networkingDebugService.config.test {
      awaitUntil { !it.failF8eRequests }
    }

    // Check that f8e requests are successful again
    f8eNetworkReachabilityService.checkConnection(client, F8eEnvironment.Local).shouldBeOk()
  }
})
