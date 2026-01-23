package build.wallet.platform.connectivity

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

/**
 * JVM implementation of [InternetConnectionChecker] for integration testing.
 *
 * Unlike Android/iOS which use real platform network APIs, this JVM implementation
 * provides a controllable [fakeIsConnected] property for simulating different
 * network states in integration tests.
 *
 * By default, returns true (network available).
 */
@BitkeyInject(AppScope::class)
class InternetConnectionCheckerImpl : InternetConnectionChecker {
  /**
   * The fake result to return. Set this in tests to simulate different network states.
   * Defaults to true (network available).
   */
  var fakeIsConnected: Boolean = true

  override fun isConnected(): Boolean {
    return fakeIsConnected
  }

  fun reset() {
    fakeIsConnected = true
  }
}
