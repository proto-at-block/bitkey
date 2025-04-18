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
import kotlinx.coroutines.test.TestScope

class HideBalancePreferenceImplTests : FunSpec({
  val eventTracker = EventTrackerMock(turbines::create)

  val hideBalancePreference = HideBalancePreferenceImpl(
    databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory),
    eventTracker = eventTracker,
    appCoroutineScope = TestScope()
  )

  beforeEach {
    hideBalancePreference.clear()
  }

  test("get biometric preference before a value is set") {
    hideBalancePreference.get().get()
      .shouldNotBeNull()
      .shouldBeFalse()
  }

  test("set biometric preference to true") {
    hideBalancePreference.set(true)

    hideBalancePreference.get().get()
      .shouldNotBeNull()
      .shouldBeTrue()

    eventTracker.eventCalls
      .awaitItem()
      .action
      .shouldBe(Action.ACTION_APP_HIDE_BALANCE_BY_DEFAULT_ENABLED)
  }

  test("set biometric preference to false") {
    hideBalancePreference.set(false)

    hideBalancePreference.get().get()
      .shouldNotBeNull()
      .shouldBeFalse()

    eventTracker.eventCalls
      .awaitItem()
      .action
      .shouldBe(Action.ACTION_APP_HIDE_BALANCE_BY_DEFAULT_DISABLED)
  }
})
