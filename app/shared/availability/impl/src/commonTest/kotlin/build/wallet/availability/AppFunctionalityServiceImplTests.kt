@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.availability

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.availability.AppFunctionalityStatus.FullFunctionality
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.AuthSignatureStatus.Unauthenticated
import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.F8e
import build.wallet.availability.NetworkReachability.REACHABLE
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

class AppFunctionalityServiceImplTests : FunSpec({

  coroutineTestScope = true

  val accountService = AccountServiceFake()
  val debugOptionsService = DebugOptionsServiceFake()
  val clock = ClockFake()
  val networkReachabilityProvider = NetworkReachabilityProviderFake()
  val f8eAuthSignatureStatusProvider = F8eAuthSignatureStatusProviderImpl()
  lateinit var networkReachabilityEventDao: NetworkReachabilityEventDao
  lateinit var service: AppFunctionalityServiceImpl

  beforeTest {
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)
    debugOptionsService.reset()
    clock.reset()
    networkReachabilityProvider.reset()
    f8eAuthSignatureStatusProvider.clear()

    networkReachabilityEventDao = NetworkReachabilityEventDaoImpl(
      clock = clock,
      databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory)
    )

    service = AppFunctionalityServiceImpl(
      accountService = accountService,
      debugOptionsService = debugOptionsService,
      networkReachabilityEventDao = networkReachabilityEventDao,
      networkReachabilityProvider = networkReachabilityProvider,
      f8eAuthSignatureStatusProvider = f8eAuthSignatureStatusProvider,
      appVariant = Customer
    )
  }

  test("assume full functionality by default before syncing") {
    service.status.test {
      awaitItem().shouldBe(FullFunctionality)
    }
  }

  test("limited functionality on start when internet becomes unreachable") {
    networkReachabilityProvider.internetReachabilityFlow.value = UNREACHABLE

    backgroundScope.launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      awaitItem().shouldBe(
        LimitedFunctionality(
          cause = InternetUnreachable(
            lastReachableTime = null,
            lastElectrumSyncReachableTime = null
          )
        )
      )
    }
  }

  test("eventual limited functionality when internet becomes unreachable") {
    networkReachabilityEventDao.insertReachabilityEvent(
      connection = NetworkConnection.ElectrumSyncerNetworkConnection,
      reachability = REACHABLE
    )
    networkReachabilityProvider.internetReachabilityFlow.value = UNREACHABLE

    backgroundScope.launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      awaitItem().shouldBe(
        LimitedFunctionality(
          cause = InternetUnreachable(
            lastReachableTime = clock.now,
            lastElectrumSyncReachableTime = clock.now
          )
        )
      )
    }
  }

  test("inactive app status when unauthenticated") {
    f8eAuthSignatureStatusProvider.updateAuthSignatureStatus(Unauthenticated)

    backgroundScope.launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      awaitItem().shouldBe(
        LimitedFunctionality(
          cause = InactiveApp
        )
      )
    }
  }

  test("limited f8e functionality when f8e becomes unreachable") {
    networkReachabilityProvider.f8eEnvironmentReachabilityFlow.value = UNREACHABLE

    backgroundScope.launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      awaitItem().shouldBe(
        LimitedFunctionality(
          cause = F8eUnreachable(
            lastReachableTime = null
          )
        )
      )
    }
  }

  test("eventual limited f8e functionality when f8e becomes unreachable") {
    networkReachabilityEventDao.insertReachabilityEvent(
      connection = F8e(Development),
      reachability = REACHABLE
    )
    networkReachabilityProvider.f8eEnvironmentReachabilityFlow.value = UNREACHABLE

    backgroundScope.launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      awaitItem().shouldBe(
        LimitedFunctionality(
          cause = F8eUnreachable(
            lastReachableTime = clock.now
          )
        )
      )
    }
  }

  test("emergency access variant has limited emergecy access mode") {
    service = AppFunctionalityServiceImpl(
      accountService = accountService,
      debugOptionsService = debugOptionsService,
      networkReachabilityEventDao = networkReachabilityEventDao,
      networkReachabilityProvider = networkReachabilityProvider,
      f8eAuthSignatureStatusProvider = f8eAuthSignatureStatusProvider,
      appVariant = Emergency
    )

    backgroundScope.launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(
        LimitedFunctionality(
          cause = EmergencyAccessMode
        )
      )
    }
  }
})
