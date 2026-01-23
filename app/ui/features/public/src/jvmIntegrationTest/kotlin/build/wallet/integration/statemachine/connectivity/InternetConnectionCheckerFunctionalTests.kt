package build.wallet.integration.statemachine.connectivity

import app.cash.turbine.test
import build.wallet.availability.AppFunctionalityStatus.FullFunctionality
import build.wallet.availability.AppFunctionalityStatus.LimitedFunctionality
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Available
import build.wallet.availability.FunctionalityFeatureStates.FeatureState.Unavailable
import build.wallet.availability.InternetUnreachable
import build.wallet.availability.NetworkConnection.HttpClientNetworkConnection
import build.wallet.availability.NetworkReachability.REACHABLE
import build.wallet.availability.NetworkReachability.UNREACHABLE
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.f8e.F8eEnvironment
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Integration tests for [InternetConnectionChecker] feature.
 *
 * These tests verify the full flow from connectivity check to AppFunctionalityService status:
 * - InternetConnectionChecker.isConnected() controls platform-level connectivity
 * - NetworkReachabilityProvider updates reachability flows
 * - AppFunctionalityService emits LimitedFunctionality status
 */
class InternetConnectionCheckerFunctionalTests : FunSpec({

  test("AppFunctionalityService shows LimitedFunctionality when internet connection is unavailable") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    // Verify initial state is FullFunctionality
    app.appFunctionalityService.status.value.shouldBe(FullFunctionality)

    // Set internet as unavailable via the JVM implementation
    app.jvmInternetConnectionChecker.fakeIsConnected = false

    // Trigger a network check to update reachability flows
    // This simulates what happens when a network request fails
    app.networkReachabilityProvider.updateNetworkReachabilityForConnection(
      httpClient = null,
      reachability = UNREACHABLE,
      connection = HttpClientNetworkConnection.F8e(F8eEnvironment.Local)
    )

    // Wait for status flow to update using turbine
    app.appFunctionalityService.status.test {
      val status = awaitUntil<LimitedFunctionality>()
      status.cause.shouldBeInstanceOf<InternetUnreachable>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("AppFunctionalityService returns to FullFunctionality when internet connection is restored") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    // Go offline first
    app.jvmInternetConnectionChecker.fakeIsConnected = false
    app.networkReachabilityProvider.updateNetworkReachabilityForConnection(
      httpClient = null,
      reachability = UNREACHABLE,
      connection = HttpClientNetworkConnection.F8e(F8eEnvironment.Local)
    )

    // Wait for offline status
    app.appFunctionalityService.status.test {
      awaitUntil<LimitedFunctionality>()

      // Restore connection
      app.jvmInternetConnectionChecker.fakeIsConnected = true
      app.networkReachabilityProvider.updateNetworkReachabilityForConnection(
        httpClient = null,
        reachability = REACHABLE,
        connection = HttpClientNetworkConnection.F8e(F8eEnvironment.Local)
      )

      // Wait for full functionality to be restored
      awaitUntil { it == FullFunctionality }

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("InternetUnreachable cause contains timestamp information") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    // Go offline
    app.jvmInternetConnectionChecker.fakeIsConnected = false
    app.networkReachabilityProvider.updateNetworkReachabilityForConnection(
      httpClient = null,
      reachability = UNREACHABLE,
      connection = HttpClientNetworkConnection.F8e(F8eEnvironment.Local)
    )

    // Wait for offline status and verify timestamp
    app.appFunctionalityService.status.test {
      val status = awaitUntil<LimitedFunctionality>()
      val cause = status.cause
      cause.shouldBeInstanceOf<InternetUnreachable>()

      // lastReachableTime should be set from the previous REACHABLE state
      (cause as InternetUnreachable).lastReachableTime.shouldNotBeNull()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("LimitedFunctionality correctly disables features when offline") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    // Go offline
    app.jvmInternetConnectionChecker.fakeIsConnected = false
    app.networkReachabilityProvider.updateNetworkReachabilityForConnection(
      httpClient = null,
      reachability = UNREACHABLE,
      connection = HttpClientNetworkConnection.F8e(F8eEnvironment.Local)
    )

    // Wait for offline status and verify feature states
    app.appFunctionalityService.status.test {
      val status = awaitUntil<LimitedFunctionality>()

      // Verify features are appropriately disabled per InternetUnreachable rules
      val featureStates = status.featureStates
      featureStates.send.shouldBeInstanceOf<Unavailable>()
      featureStates.receive.shouldBeInstanceOf<Available>()
      featureStates.deposit.shouldBeInstanceOf<Unavailable>()
      featureStates.sell.shouldBeInstanceOf<Unavailable>()

      cancelAndIgnoreRemainingEvents()
    }
  }
})
