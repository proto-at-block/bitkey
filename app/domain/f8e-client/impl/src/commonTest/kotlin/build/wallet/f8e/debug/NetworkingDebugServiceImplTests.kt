package build.wallet.f8e.debug

import build.wallet.platform.config.AppVariant
import build.wallet.store.KeyValueStoreFactory
import build.wallet.store.KeyValueStoreFactoryFake
import com.github.michaelbull.result.Ok
import com.russhwolf.settings.coroutines.SuspendSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Verifies [NetworkingDebugServiceImpl] behavior across app variants:
 *
 * - On [AppVariant.Customer] and [AppVariant.Emergency] (no debug menu), the service
 *   never touches persistence and the config is permanently the compile-time default.
 * - On debug variants, the config is persisted to a plain key-value store and restored
 *   by [NetworkingDebugService.launchSync].
 */
class NetworkingDebugServiceImplTests : FunSpec({

  lateinit var keyValueStoreFactory: KeyValueStoreFactoryFake

  beforeTest {
    keyValueStoreFactory = KeyValueStoreFactoryFake()
  }

  listOf(AppVariant.Customer, AppVariant.Emergency).forEach { variant ->
    test("$variant: launchSync and setFailF8eRequests never access persistence") {
      val service = NetworkingDebugServiceImpl(keyValueStoreFactory, variant)

      // launchSync must return immediately.
      service.launchSync()

      service.setFailF8eRequests(value = true) shouldBe Ok(Unit)

      // Config remains the compile-time default; nothing was persisted.
      service.config.value shouldBe NetworkingDebugConfig(failF8eRequests = false)
      keyValueStoreFactory.store.size shouldBe 0
    }
  }

  test("Team: setFailF8eRequests persists and updates config state") {
    val service = NetworkingDebugServiceImpl(keyValueStoreFactory, AppVariant.Team)
    service.launchSync()

    service.config.value shouldBe NetworkingDebugConfig(failF8eRequests = false)

    service.setFailF8eRequests(value = true) shouldBe Ok(Unit)
    service.config.value shouldBe NetworkingDebugConfig(failF8eRequests = true)

    service.setFailF8eRequests(value = false) shouldBe Ok(Unit)
    service.config.value shouldBe NetworkingDebugConfig(failF8eRequests = false)
  }

  test("Team: launchSync falls back to default when the store read fails") {
    val failingFactory = object : KeyValueStoreFactory {
      override suspend fun getOrCreate(storeName: String): SuspendSettings {
        val delegate = keyValueStoreFactory.getOrCreate(storeName)
        return object : SuspendSettings by delegate {
          override suspend fun getBoolean(
            key: String,
            defaultValue: Boolean,
          ): Boolean = error("simulated store read failure")
        }
      }
    }
    val service = NetworkingDebugServiceImpl(failingFactory, AppVariant.Team)

    // launchSync runs as an AppWorker at startup; a failed read must not throw.
    service.launchSync()
    service.config.value shouldBe NetworkingDebugConfig(failF8eRequests = false)
  }

  test("Team: launchSync restores persisted config") {
    val service = NetworkingDebugServiceImpl(keyValueStoreFactory, AppVariant.Team)
    service.setFailF8eRequests(value = true) shouldBe Ok(Unit)

    // New service instance sharing the same store (simulates app restart).
    val restartedService = NetworkingDebugServiceImpl(keyValueStoreFactory, AppVariant.Team)
    restartedService.config.value shouldBe NetworkingDebugConfig(failF8eRequests = false)
    restartedService.launchSync()
    restartedService.config.value shouldBe NetworkingDebugConfig(failF8eRequests = true)
  }
})
