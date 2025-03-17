package build.wallet.inappsecurity

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class BiometricPreferenceImplTests : FunSpec({
  val eventTracker = EventTrackerMock(turbines::create)

  val biometricPreference = BiometricPreferenceImpl(
    databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory),
    eventTracker = eventTracker
  )

  beforeEach {
    biometricPreference.clear()
  }

  test("get biometric preference before a value is set") {
    biometricPreference.get().get()
      .shouldNotBeNull()
      .shouldBeFalse()
  }

  test("set biometric preference to true") {
    biometricPreference.set(true)

    biometricPreference.get().get()
      .shouldNotBeNull()
      .shouldBeTrue()

    eventTracker.eventCalls
      .awaitItem()
      .action
      .shouldBe(Action.ACTION_APP_BIOMETRICS_ENABLED)
  }

  test("set biometric preference to false") {
    biometricPreference.set(false)

    biometricPreference.get().get()
      .shouldNotBeNull()
      .shouldBeFalse()

    eventTracker.eventCalls
      .awaitItem()
      .action
      .shouldBe(Action.ACTION_APP_BIOMETRICS_DISABLED)
  }
})
