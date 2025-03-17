package build.wallet.availability

import app.cash.turbine.test
import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.F8e
import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.Memfault
import build.wallet.availability.NetworkReachability.REACHABLE
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.HttpError
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.combine

class NetworkReachabilityProviderImplTests : FunSpec({
  val f8eNetworkReachabilityF8eClient = F8eNetworkReachabilityServiceMock(turbines::create)
  val internetNetworkReachabilityService = InternetNetworkReachabilityServiceMock(turbines::create)
  val networkReachabilityEventDao = NetworkReachabilityEventDaoMock(turbines::create)
  lateinit var provider: NetworkReachabilityProviderImpl
  val client = HttpClient()

  beforeEach {
    f8eNetworkReachabilityF8eClient.reset()
    internetNetworkReachabilityService.reset()

    provider =
      NetworkReachabilityProviderImpl(
        f8eNetworkReachabilityService = f8eNetworkReachabilityF8eClient,
        internetNetworkReachabilityService = internetNetworkReachabilityService,
        networkReachabilityEventDao = networkReachabilityEventDao
      )
  }

  test("Flows start out as REACHABLE") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))
    }
  }

  test("No emission when update to UNREACHABLE but reachability service is Ok") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))

      provider.updateNetworkReachabilityForConnection(client, UNREACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()
      f8eNetworkReachabilityF8eClient.checkConnectionCalls.awaitItem()
      internetNetworkReachabilityService.checkConnectionCalls.awaitItem()

      expectNoEvents()
    }
  }

  test("Only f8e flow emits when update to UNREACHABLE and reachability service is Err") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))

      f8eNetworkReachabilityF8eClient.checkConnectionResult = Err(HttpError.UnhandledException(Throwable()))
      provider.updateNetworkReachabilityForConnection(client, UNREACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()
      f8eNetworkReachabilityF8eClient.checkConnectionCalls.awaitItem()
      internetNetworkReachabilityService.checkConnectionCalls.awaitItem()

      awaitItem().shouldBe(Pair(UNREACHABLE, REACHABLE))
    }
  }

  test("Both flows emit when f8e updates to REACHABLE") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))

      f8eNetworkReachabilityF8eClient.checkConnectionResult = Err(HttpError.UnhandledException(Throwable()))
      internetNetworkReachabilityService.checkConnectionResult = Err(HttpError.UnhandledException(Throwable()))
      provider.updateNetworkReachabilityForConnection(client, UNREACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()
      f8eNetworkReachabilityF8eClient.checkConnectionCalls.awaitItem()
      internetNetworkReachabilityService.checkConnectionCalls.awaitItem()

      awaitItem().shouldBe(Pair(UNREACHABLE, REACHABLE))
      awaitItem().shouldBe(Pair(UNREACHABLE, UNREACHABLE))

      provider.updateNetworkReachabilityForConnection(client, REACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()

      awaitItem().shouldBe(Pair(REACHABLE, UNREACHABLE))
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))
    }
  }

  test("Only internet updated when non-f8e updates to REACHABLE") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))

      f8eNetworkReachabilityF8eClient.checkConnectionResult = Err(HttpError.UnhandledException(Throwable()))
      internetNetworkReachabilityService.checkConnectionResult = Err(HttpError.UnhandledException(Throwable()))
      provider.updateNetworkReachabilityForConnection(client, UNREACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()
      f8eNetworkReachabilityF8eClient.checkConnectionCalls.awaitItem()
      internetNetworkReachabilityService.checkConnectionCalls.awaitItem()

      awaitItem().shouldBe(Pair(UNREACHABLE, REACHABLE))
      awaitItem().shouldBe(Pair(UNREACHABLE, UNREACHABLE))

      provider.updateNetworkReachabilityForConnection(client, REACHABLE, Memfault)
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()
      f8eNetworkReachabilityF8eClient.checkConnectionCalls.awaitItem()

      awaitItem().shouldBe(Pair(UNREACHABLE, REACHABLE))
    }
  }
})

private fun NetworkReachabilityProviderImpl.f8eAndInternetPairFlow() =
  f8eReachabilityFlow(F8eEnvironment.Production)
    .combine(internetReachabilityFlow()) { a, b -> Pair(a, b) }
