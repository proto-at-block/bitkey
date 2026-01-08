@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.availability

import app.cash.turbine.test
import bitkey.account.AccountConfigServiceFake
import build.wallet.availability.AppFunctionalityStatus.FullFunctionality
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.AuthSignatureStatus.Unauthenticated
import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection.F8e
import build.wallet.availability.NetworkReachability.REACHABLE
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.coroutines.createBackgroundScope
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

class AppFunctionalityServiceImplTests : FunSpec({

  val accountConfigService = AccountConfigServiceFake()
  val clock = ClockFake()
  val networkReachabilityProvider = NetworkReachabilityProviderFake()
  val f8eAuthSignatureStatusProvider = F8eAuthSignatureStatusProviderImpl()
  lateinit var networkReachabilityEventDao: NetworkReachabilityEventDao
  lateinit var service: AppFunctionalityServiceImpl

  beforeTest {
    accountConfigService.reset()
    clock.reset()
    networkReachabilityProvider.reset()
    f8eAuthSignatureStatusProvider.clear()

    networkReachabilityEventDao = NetworkReachabilityEventDaoImpl(
      clock = clock,
      databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory)
    )

    service = AppFunctionalityServiceImpl(
      accountConfigService = accountConfigService,
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
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      networkReachabilityProvider.internetReachabilityFlow.value = UNREACHABLE

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
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      networkReachabilityEventDao.insertReachabilityEvent(
        connection = NetworkConnection.ElectrumSyncerNetworkConnection,
        reachability = REACHABLE
      )
      networkReachabilityProvider.internetReachabilityFlow.value = UNREACHABLE

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
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      f8eAuthSignatureStatusProvider.updateAuthSignatureStatus(Unauthenticated)

      awaitItem().shouldBe(
        LimitedFunctionality(
          cause = InactiveApp
        )
      )
    }
  }

  test("limited f8e functionality when f8e becomes unreachable") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      networkReachabilityProvider.f8eEnvironmentReachabilityFlow.value = UNREACHABLE

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
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      networkReachabilityEventDao.insertReachabilityEvent(
        connection = F8e(Development),
        reachability = REACHABLE
      )
      networkReachabilityProvider.f8eEnvironmentReachabilityFlow.value = UNREACHABLE

      awaitItem().shouldBe(
        LimitedFunctionality(
          cause = F8eUnreachable(
            lastReachableTime = clock.now
          )
        )
      )
    }
  }

  test("Emergency Exit Kit variant has limited emergency access mode") {
    service = AppFunctionalityServiceImpl(
      accountConfigService = accountConfigService,
      networkReachabilityEventDao = networkReachabilityEventDao,
      networkReachabilityProvider = networkReachabilityProvider,
      f8eAuthSignatureStatusProvider = f8eAuthSignatureStatusProvider,
      appVariant = Emergency
    )

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(
        LimitedFunctionality(
          cause = EmergencyExitMode
        )
      )
    }
  }

  test("EEK recovery in normal app has limited emergency access mode") {
    createBackgroundScope().launch {
      service.executeWork()
    }

    service.status.test {
      awaitItem().shouldBe(FullFunctionality) // default value

      accountConfigService.setF8eEnvironment(F8eEnvironment.ForceOffline)

      awaitItem().shouldBe(
        LimitedFunctionality(
          cause = EmergencyExitMode
        )
      )
    }
  }
})
