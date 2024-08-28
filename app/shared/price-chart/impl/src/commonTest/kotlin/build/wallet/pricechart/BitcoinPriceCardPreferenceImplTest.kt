package build.wallet.pricechart

import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.BitcoinPriceChartFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.sqldelight.inMemorySqlDriver
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope

class BitcoinPriceCardPreferenceImplTest : FunSpec({
  val eventTracker = EventTrackerMock(turbines::create)

  val featureFlag = BitcoinPriceChartFeatureFlag(FeatureFlagDaoFake())
  val cardPreference = BitcoinPriceCardPreferenceImpl(
    databaseProvider = BitkeyDatabaseProviderImpl(inMemorySqlDriver().factory),
    eventTracker = eventTracker,
    bitcoinPriceChartFeatureFlag = featureFlag,
    appCoroutineScope = TestScope()
  )

  beforeEach {
    featureFlag.setFlagValue(true)
    cardPreference.clear()
  }

  test("get card preference before a value is set") {
    cardPreference.get().get()
      .shouldNotBeNull()
      .shouldBeTrue()
  }

  test("get card preference always false when feature flag is disabled") {
    cardPreference.set(true)
    featureFlag.setFlagValue(false)

    cardPreference.get().get()
      .shouldNotBeNull()
      .shouldBeFalse()

    eventTracker.eventCalls.awaitItem()
  }

  test("set card preference to true") {
    cardPreference.set(true)

    cardPreference.get().get()
      .shouldNotBeNull()
      .shouldBeTrue()

    eventTracker.eventCalls
      .awaitItem()
      .action
      .shouldBe(Action.ACTION_APP_BITCOIN_PRICE_CARD_ENABLED)
  }

  test("set card preference to false") {
    cardPreference.set(false)

    cardPreference.get().get()
      .shouldNotBeNull()
      .shouldBeFalse()

    eventTracker.eventCalls
      .awaitItem()
      .action
      .shouldBe(Action.ACTION_APP_BITCOIN_PRICE_CARD_DISABLED)
  }
})
