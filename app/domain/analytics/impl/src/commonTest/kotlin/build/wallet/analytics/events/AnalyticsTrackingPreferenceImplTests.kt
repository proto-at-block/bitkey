package build.wallet.analytics.events

import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.platform.config.AppVariant
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class AnalyticsTrackingPreferenceImplTests : FunSpec({
  test("preference is always true in customer builds") {
    val preference = AnalyticsTrackingPreferenceImpl(
      appVariant = AppVariant.Customer,
      databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory)
    )

    preference.get().shouldBeTrue()
    preference.set(false)
    preference.get().shouldBeTrue()
  }

  test("preference is always true in Emergency builds") {
    val preference = AnalyticsTrackingPreferenceImpl(
      appVariant = AppVariant.Emergency,
      databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory)
    )

    preference.get().shouldBeTrue()
    preference.set(false)
    preference.get().shouldBeTrue()
  }

  test("can be updated in non-customer builds") {
    val preference = AnalyticsTrackingPreferenceImpl(
      appVariant = AppVariant.Development,
      databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory)
    )

    preference.set(true)
    preference.get().shouldBeTrue()

    preference.set(false)
    preference.get().shouldBeFalse()
  }
})
