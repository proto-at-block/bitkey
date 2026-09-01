package build.wallet.analytics.events

import app.cash.turbine.test
import build.wallet.platform.config.AppVariant
import build.wallet.store.KeyValueStoreFactory
import build.wallet.store.KeyValueStoreFactoryFake
import com.russhwolf.settings.coroutines.SuspendSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class AnalyticsTrackingPreferenceImplTests : FunSpec({

  lateinit var keyValueStoreFactory: KeyValueStoreFactoryFake

  beforeTest {
    keyValueStoreFactory = KeyValueStoreFactoryFake()
  }

  fun preference(appVariant: AppVariant) =
    AnalyticsTrackingPreferenceImpl(
      appVariant = appVariant,
      keyValueStoreFactory = keyValueStoreFactory
    )

  test("preference is always true in customer builds") {
    val preference = preference(AppVariant.Customer)

    preference.get().shouldBeTrue()
    preference.set(false)
    preference.get().shouldBeTrue()
    // Customer builds never persist anything.
    keyValueStoreFactory.store.size shouldBe 0
  }

  test("preference is always true in Emergency builds") {
    val preference = preference(AppVariant.Emergency)

    preference.get().shouldBeTrue()
    preference.set(false)
    preference.get().shouldBeTrue()
    keyValueStoreFactory.store.size shouldBe 0
  }

  test("preference defaults to true in team builds") {
    preference(AppVariant.Team).get().shouldBeTrue()
  }

  test("preference defaults to false in development builds") {
    preference(AppVariant.Development).get().shouldBeFalse()
  }

  test("preference defaults to false in alpha builds") {
    preference(AppVariant.Alpha).get().shouldBeFalse()
  }

  test("can be updated in non-customer builds") {
    val preference = preference(AppVariant.Development)

    preference.set(true)
    preference.get().shouldBeTrue()

    preference.set(false)
    preference.get().shouldBeFalse()
  }

  test("value persists across instances sharing the same store") {
    preference(AppVariant.Team).set(false)

    // New instance simulates app restart.
    preference(AppVariant.Team).get().shouldBeFalse()
  }

  test("isEnabled emits updates in non-customer builds") {
    val preference = preference(AppVariant.Team)

    preference.isEnabled().test {
      awaitItem().shouldBeTrue()
      preference.set(false)
      awaitItem().shouldBeFalse()
      preference.set(true)
      awaitItem().shouldBeTrue()
    }
  }

  test("isEnabled always emits true in customer builds") {
    val preference = preference(AppVariant.Customer)

    preference.isEnabled().test {
      awaitItem().shouldBeTrue()
      awaitComplete()
    }
  }

  test("failed write does not update in-memory state") {
    val failingFactory = object : KeyValueStoreFactory {
      var failWrites = false

      override suspend fun getOrCreate(storeName: String): SuspendSettings {
        val delegate = keyValueStoreFactory.getOrCreate(storeName)
        return object : SuspendSettings by delegate {
          override suspend fun putBoolean(
            key: String,
            value: Boolean,
          ) {
            if (failWrites) error("simulated store write failure")
            delegate.putBoolean(key, value)
          }
        }
      }
    }
    val preference = AnalyticsTrackingPreferenceImpl(
      appVariant = AppVariant.Team,
      keyValueStoreFactory = failingFactory
    )

    // Persist an explicit opt-out successfully.
    preference.set(false)
    preference.get().shouldBeFalse()

    // A failed write must not flip the in-memory value: get()/isEnabled()
    // would otherwise report a value that reverts on next launch.
    failingFactory.failWrites = true
    preference.set(true)
    preference.get().shouldBeFalse()

    // And a new instance reading the durable store agrees.
    preference(AppVariant.Team).get().shouldBeFalse()
  }

  test("failed read falls back to variant default instead of crashing") {
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

    AnalyticsTrackingPreferenceImpl(
      appVariant = AppVariant.Team,
      keyValueStoreFactory = failingFactory
    ).get().shouldBeTrue() // Team default.

    AnalyticsTrackingPreferenceImpl(
      appVariant = AppVariant.Development,
      keyValueStoreFactory = failingFactory
    ).get().shouldBeFalse() // Development default.
  }
})
