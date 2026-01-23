package build.wallet.availability

import app.cash.turbine.test
import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.F8e
import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.Memfault
import build.wallet.availability.NetworkReachability.REACHABLE
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.HttpError
import build.wallet.platform.connectivity.InternetConnectionCheckerFake
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.combine

class NetworkReachabilityProviderImplTests : FunSpec({
  val f8eNetworkReachabilityF8eClient = F8eNetworkReachabilityServiceMock(turbines::create)
  val internetConnectionChecker = InternetConnectionCheckerFake()
  val networkReachabilityEventDao = NetworkReachabilityEventDaoMock(turbines::create)
  lateinit var provider: NetworkReachabilityProviderImpl
  val client = HttpClient()

  beforeEach {
    f8eNetworkReachabilityF8eClient.reset()
    internetConnectionChecker.reset()

    provider =
      NetworkReachabilityProviderImpl(
        f8eNetworkReachabilityService = f8eNetworkReachabilityF8eClient,
        internetConnectionChecker = internetConnectionChecker,
        networkReachabilityEventDao = networkReachabilityEventDao
      )
  }

  test("Flows start out as REACHABLE") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))
    }
  }

  test("No emission when update to UNREACHABLE but platform reports connection") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))

      // Platform says we have connection, so no change to internet flow
      internetConnectionChecker.connected = true
      provider.updateNetworkReachabilityForConnection(client, UNREACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()
      f8eNetworkReachabilityF8eClient.checkConnectionCalls.awaitItem()

      expectNoEvents()
    }
  }

  test("Only f8e flow emits when update to UNREACHABLE and f8e check fails but platform has connection") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))

      f8eNetworkReachabilityF8eClient.checkConnectionResult = Err(HttpError.UnhandledException(Throwable()))
      internetConnectionChecker.connected = true
      provider.updateNetworkReachabilityForConnection(client, UNREACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()
      f8eNetworkReachabilityF8eClient.checkConnectionCalls.awaitItem()

      awaitItem().shouldBe(Pair(UNREACHABLE, REACHABLE))
    }
  }

  test("Both flows emit when f8e updates to UNREACHABLE and platform has no connection") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))

      // Set up: platform reports no connection
      internetConnectionChecker.connected = false

      // Act: trigger unreachable update
      provider.updateNetworkReachabilityForConnection(client, UNREACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()
      // Note: F8e check is skipped when internet is unreachable (early return optimization)

      // Assert: internet is updated first (correct cause attribution for InternetUnreachable)
      awaitItem().shouldBe(Pair(REACHABLE, UNREACHABLE))

      // Assert: then F8e is updated
      awaitItem().shouldBe(Pair(UNREACHABLE, UNREACHABLE))
    }
  }

  test("Both flows emit when f8e updates to REACHABLE") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))

      // Set up: go offline
      internetConnectionChecker.connected = false

      // Act: trigger unreachable update
      provider.updateNetworkReachabilityForConnection(client, UNREACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()

      // Assert: both become unreachable (internet first, then F8e)
      awaitItem().shouldBe(Pair(REACHABLE, UNREACHABLE))
      awaitItem().shouldBe(Pair(UNREACHABLE, UNREACHABLE))

      // Set up: platform reports connection is back
      internetConnectionChecker.connected = true

      // Act: trigger reachable update
      provider.updateNetworkReachabilityForConnection(client, REACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()

      // Assert: both become reachable (F8e first, then internet)
      awaitItem().shouldBe(Pair(REACHABLE, UNREACHABLE))
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))
    }
  }

  test("Only internet updated when non-f8e updates to REACHABLE") {
    provider.f8eAndInternetPairFlow().test {
      awaitItem().shouldBe(Pair(REACHABLE, REACHABLE))

      // Set up: go offline
      internetConnectionChecker.connected = false

      // Act: trigger unreachable update via F8e
      provider.updateNetworkReachabilityForConnection(client, UNREACHABLE, F8e(F8eEnvironment.Production))
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()

      // Assert: both become unreachable (internet first, then F8e)
      awaitItem().shouldBe(Pair(REACHABLE, UNREACHABLE))
      awaitItem().shouldBe(Pair(UNREACHABLE, UNREACHABLE))

      // Set up: platform reports connection is back, but F8e check will fail
      internetConnectionChecker.connected = true
      f8eNetworkReachabilityF8eClient.checkConnectionResult = Err(HttpError.UnhandledException(Throwable()))

      // Act: trigger reachable update via non-F8e connection (Memfault)
      provider.updateNetworkReachabilityForConnection(client, REACHABLE, Memfault)
      networkReachabilityEventDao.insertReachabilityEventCalls.awaitItem()
      f8eNetworkReachabilityF8eClient.checkConnectionCalls.awaitItem()

      // Assert: only internet becomes reachable (F8e check failed, so F8e stays unreachable)
      awaitItem().shouldBe(Pair(UNREACHABLE, REACHABLE))
    }
  }

  test("hasInternetConnection delegates to internet connection checker") {
    internetConnectionChecker.connected = true
    provider.hasInternetConnection().shouldBe(true)

    internetConnectionChecker.connected = false
    provider.hasInternetConnection().shouldBe(false)
  }
})

private fun NetworkReachabilityProviderImpl.f8eAndInternetPairFlow() =
  f8eReachabilityFlow(F8eEnvironment.Production)
    .combine(internetReachabilityFlow()) { a, b -> Pair(a, b) }
