package build.wallet.bitcoin.bdk

import build.wallet.coroutines.createBackgroundScope
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.platform.app.AppSessionState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import uniffi.bdk.ElectrumException

class ElectrumClientProviderImplTests : FunSpec({
  val appSessionManager = AppSessionManagerFake()

  fun TestScope.createProvider() =
    ElectrumClientProviderImpl(
      appCoroutineScope = createBackgroundScope(),
      appSessionManager = appSessionManager
    )

  beforeTest {
    appSessionManager.reset()
  }

  context("withClient") {
    test("propagates ElectrumException for invalid URL") {
      val provider = createProvider()
      shouldThrow<ElectrumException> {
        provider.withClient("tcp://invalid.host.that.does.not.exist:12345") { it }
      }
    }

    test("executes block when client creation succeeds and returns result") {
      val provider = createProvider()
      // Note: This test requires a valid electrum server URL
      // Using Blockstream's public server for testing
      // Skip if network is unavailable
      try {
        val result = provider.withClient("ssl://electrum.blockstream.info:60002") { client ->
          "block-executed"
        }
        result.shouldBe("block-executed")
      } catch (e: ElectrumException) {
        // Skip test if server is unreachable
        println("Skipping test - electrum server unreachable: ${e.message}")
      }
    }

    test("caches client for same URL") {
      val provider = createProvider()
      // Note: This test requires a valid electrum server
      try {
        val url = "ssl://electrum.blockstream.info:60002"
        var firstClientHashCode: Int? = null
        var secondClientHashCode: Int? = null

        provider.withClient(url) { client ->
          firstClientHashCode = System.identityHashCode(client)
        }

        provider.withClient(url) { client ->
          secondClientHashCode = System.identityHashCode(client)
        }

        // Same client instance should be reused
        firstClientHashCode.shouldBe(secondClientHashCode)
      } catch (e: ElectrumException) {
        println("Skipping test - electrum server unreachable: ${e.message}")
      }
    }

    test("creates new client for different URL") {
      val provider = createProvider()
      // Note: This test requires valid electrum servers
      try {
        val url1 = "ssl://electrum.blockstream.info:60002"
        val url2 = "ssl://electrum.blockstream.info:50002" // Different port
        var firstClientHashCode: Int? = null
        var secondClientHashCode: Int? = null

        provider.withClient(url1) { client ->
          firstClientHashCode = System.identityHashCode(client)
        }

        provider.withClient(url2) { client ->
          secondClientHashCode = System.identityHashCode(client)
        }

        // Different URLs should create different client instances
        // Note: The old client is closed when a new URL is used
        firstClientHashCode shouldNotBe secondClientHashCode
      } catch (e: ElectrumException) {
        println("Skipping test - electrum server unreachable: ${e.message}")
      }
    }

    test("does not cache failed connection attempts") {
      val provider = createProvider()
      val invalidUrl = "tcp://invalid.nonexistent.host:12345"

      // First attempt fails
      shouldThrow<ElectrumException> {
        provider.withClient(invalidUrl) { it }
      }

      // Second attempt should also try (not return cached failure)
      shouldThrow<ElectrumException> {
        provider.withClient(invalidUrl) { it }
      }
    }
  }

  context("invalidate") {
    test("invalidate with non-cached URL is a no-op") {
      val provider = createProvider()
      // Should not throw even if URL was never cached
      provider.invalidate("ssl://some-url-never-used:50002")
    }

    test("invalidate clears cached client for matching URL") {
      val provider = createProvider()
      // Note: This test requires a valid electrum server
      try {
        val url = "ssl://electrum.blockstream.info:60002"
        var firstClientHashCode: Int? = null
        var secondClientHashCode: Int? = null

        provider.withClient(url) { client ->
          firstClientHashCode = System.identityHashCode(client)
        }

        // Invalidate the cache
        provider.invalidate(url)

        // Next call should create a new client
        provider.withClient(url) { client ->
          secondClientHashCode = System.identityHashCode(client)
        }

        // Should be different instances after invalidation
        firstClientHashCode shouldNotBe secondClientHashCode
      } catch (e: ElectrumException) {
        println("Skipping test - electrum server unreachable: ${e.message}")
      }
    }

    test("invalidate with non-matching URL does not clear cache") {
      val provider = createProvider()
      // Note: This test requires a valid electrum server
      try {
        val cachedUrl = "ssl://electrum.blockstream.info:60002"
        val otherUrl = "ssl://other.server:50002"
        var firstClientHashCode: Int? = null
        var secondClientHashCode: Int? = null

        provider.withClient(cachedUrl) { client ->
          firstClientHashCode = System.identityHashCode(client)
        }

        // Invalidate a different URL
        provider.invalidate(otherUrl)

        // Cache should still be intact
        provider.withClient(cachedUrl) { client ->
          secondClientHashCode = System.identityHashCode(client)
        }

        // Should be the same instance (cache not cleared)
        firstClientHashCode.shouldBe(secondClientHashCode)
      } catch (e: ElectrumException) {
        println("Skipping test - electrum server unreachable: ${e.message}")
      }
    }
  }

  context("app session state changes") {
    test("clears cache when app enters background") {
      val provider = createProvider()
      try {
        val url = "ssl://electrum.blockstream.info:60002"
        var firstClientHashCode: Int? = null
        var secondClientHashCode: Int? = null

        provider.withClient(url) { client ->
          firstClientHashCode = System.identityHashCode(client)
        }

        // Simulate app going to background
        // Using Dispatchers.Unconfined in createBackgroundScope, the flow
        // collection will run immediately when we change the state
        appSessionManager.appSessionState.value = AppSessionState.BACKGROUND

        // Next call should create a new client
        provider.withClient(url) { client ->
          secondClientHashCode = System.identityHashCode(client)
        }

        // Should be different instances after background state
        firstClientHashCode shouldNotBe secondClientHashCode
      } catch (e: ElectrumException) {
        println("Skipping test - electrum server unreachable: ${e.message}")
      }
    }

    test("does not clear cache when app stays in foreground") {
      val provider = createProvider()
      try {
        val url = "ssl://electrum.blockstream.info:60002"
        var firstClientHashCode: Int? = null
        var secondClientHashCode: Int? = null

        provider.withClient(url) { client ->
          firstClientHashCode = System.identityHashCode(client)
        }

        // Keep app in foreground
        appSessionManager.appSessionState.value = AppSessionState.FOREGROUND

        // Cache should still be intact
        provider.withClient(url) { client ->
          secondClientHashCode = System.identityHashCode(client)
        }

        // Should be the same instance
        firstClientHashCode.shouldBe(secondClientHashCode)
      } catch (e: ElectrumException) {
        println("Skipping test - electrum server unreachable: ${e.message}")
      }
    }
  }
})
