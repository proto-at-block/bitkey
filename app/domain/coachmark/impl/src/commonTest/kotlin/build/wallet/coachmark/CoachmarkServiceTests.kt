package build.wallet.coachmark

import build.wallet.account.AccountServiceFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.BalanceHistoryFeatureFlag
import build.wallet.feature.flags.CoachmarksGlobalFeatureFlag
import build.wallet.feature.flags.SecurityHubFeatureFlag
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CoachmarkServiceTests :
  FunSpec({
    val sqlDriver = inMemorySqlDriver()

    lateinit var service: CoachmarkService
    val featureFlagDao = FeatureFlagDaoMock()
    val accountService = AccountServiceFake()
    val balanceHistoryFeatureFlag = BalanceHistoryFeatureFlag(featureFlagDao)
    val coachmarksGlobalFlag = CoachmarksGlobalFeatureFlag(featureFlagDao)
    val eventTracker = EventTrackerMock(turbines::create)
    val securityHubFeatureFlag = SecurityHubFeatureFlag(featureFlagDao)

    beforeTest {
      accountService.setActiveAccount(FullAccountMock)
      balanceHistoryFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      service = CoachmarkServiceImpl(
        CoachmarkDaoImpl(BitkeyDatabaseProviderImpl(sqlDriver.factory)),
        accountService,
        CoachmarkVisibilityDecider(
          ClockFake(),
          balanceHistoryFeatureFlag,
          securityHubFeatureFlag
        ),
        coachmarksGlobalFlag,
        eventTracker,
        ClockFake()
      )
      service.resetCoachmarks()
      securityHubFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    }

    test("coachmarksToDisplay") {
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.InheritanceCoachmark,
            CoachmarkIdentifier.SecurityHubSettingsCoachmark
          )
        ).shouldBe(
          Ok(
            listOf(
              CoachmarkIdentifier.InheritanceCoachmark,
              CoachmarkIdentifier.SecurityHubSettingsCoachmark
            )
          )
        )
    }

    test("didDisplayCoachmark") {
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.InheritanceCoachmark))
        .shouldBe(Ok(listOf(CoachmarkIdentifier.InheritanceCoachmark)))
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.InheritanceCoachmark)
      eventTracker.eventCalls
        .awaitItem()
        .action
        .shouldBe(Action.ACTION_APP_COACHMARK_VIEWED_INHERITANCE)
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.InheritanceCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("resetCoachmarks") {
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.InheritanceCoachmark,
            CoachmarkIdentifier.SecurityHubSettingsCoachmark
          )
        ).shouldBe(
          Ok(
            listOf(
              CoachmarkIdentifier.InheritanceCoachmark,
              CoachmarkIdentifier.SecurityHubSettingsCoachmark
            )
          )
        )
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.InheritanceCoachmark)
      eventTracker.eventCalls
        .awaitItem()
        .action
        .shouldBe(Action.ACTION_APP_COACHMARK_VIEWED_INHERITANCE)
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.SecurityHubSettingsCoachmark)
      eventTracker.eventCalls
        .awaitItem()
        .action
        .shouldBe(Action.ACTION_APP_COACHMARK_VIEWED_SECURITY_HUB_SETTINGS)
      service.resetCoachmarks()
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.InheritanceCoachmark,
            CoachmarkIdentifier.SecurityHubSettingsCoachmark
          )
        ).shouldBe(
          Ok(
            listOf(
              CoachmarkIdentifier.InheritanceCoachmark,
              CoachmarkIdentifier.SecurityHubSettingsCoachmark
            )
          )
        )
    }

    test("no coachmarks to display for lite accounts") {
      accountService.setActiveAccount(LiteAccountMock)
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.InheritanceCoachmark,
            CoachmarkIdentifier.SecurityHubSettingsCoachmark
          )
        ).shouldBe(
          Ok(emptyList())
        )
    }

    test("don't return expired coachmarks") {
      val coachmarkDao = CoachmarkDaoFake()
      coachmarkDao.insertCoachmark(CoachmarkIdentifier.InheritanceCoachmark, Instant.DISTANT_PAST)
      service = CoachmarkServiceImpl(
        coachmarkDao,
        accountService,
        CoachmarkVisibilityDecider(
          ClockFake(),
          balanceHistoryFeatureFlag,
          securityHubFeatureFlag
        ),
        coachmarksGlobalFlag,
        eventTracker,
        ClockFake()
      )
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.InheritanceCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("don't return viewed coachmarks") {
      val coachmarkDao = CoachmarkDaoFake()
      coachmarkDao
        .insertCoachmark(CoachmarkIdentifier.InheritanceCoachmark, Instant.DISTANT_FUTURE)
      coachmarkDao.setViewed(CoachmarkIdentifier.InheritanceCoachmark)
      service = CoachmarkServiceImpl(
        coachmarkDao,
        accountService,
        CoachmarkVisibilityDecider(
          ClockFake(),
          balanceHistoryFeatureFlag,
          securityHubFeatureFlag
        ),
        coachmarksGlobalFlag,
        eventTracker,
        ClockFake()
      )
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.InheritanceCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("don't return any coachmarks if the global flag is on") {
      coachmarksGlobalFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.InheritanceCoachmark,
            CoachmarkIdentifier.SecurityHubSettingsCoachmark
          )
        ).shouldBe(
          Ok(emptyList())
        )
    }
  })
