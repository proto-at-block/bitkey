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
import build.wallet.feature.flags.Bip177FeatureFlag
import build.wallet.feature.flags.CoachmarksGlobalFeatureFlag
import build.wallet.feature.flags.PrivateWalletMigrationFeatureFlag
import build.wallet.money.display.BitcoinDisplayPreferenceRepositoryFake
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.onboarding.OnboardingCompletionServiceFake
import build.wallet.platform.config.AppVariant
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

class CoachmarkServiceTests :
  FunSpec({
    val sqlDriver = inMemorySqlDriver()

    lateinit var service: CoachmarkService
    val featureFlagDao = FeatureFlagDaoMock()
    val accountService = AccountServiceFake()
    val coachmarksGlobalFlag = CoachmarksGlobalFeatureFlag(featureFlagDao)
    val bip177FeatureFlag = Bip177FeatureFlag(featureFlagDao)
    val privateWalletMigrationFeatureFlag = PrivateWalletMigrationFeatureFlag(featureFlagDao)
    val bitcoinDisplayPreferenceRepository = BitcoinDisplayPreferenceRepositoryFake()
    val bip177CoachmarkEligibilityDao = Bip177CoachmarkEligibilityDaoFake()
    val onboardingCompletionService = OnboardingCompletionServiceFake()
    val eventTracker = EventTrackerMock(turbines::create)

    fun createVisibilityDecider(
      clock: ClockFake = ClockFake(),
      eligibilityDao: Bip177CoachmarkEligibilityDao = bip177CoachmarkEligibilityDao,
    ) = CoachmarkVisibilityDecider(
      clock = clock,
      bip177CoachmarkPolicy = Bip177CoachmarkPolicy(
        clock = clock,
        bip177FeatureFlag = bip177FeatureFlag,
        bitcoinDisplayPreferenceRepository = bitcoinDisplayPreferenceRepository,
        bip177CoachmarkEligibilityDao = eligibilityDao,
        onboardingCompletionService = onboardingCompletionService
      ),
      privateWalletMigrationFeatureFlag = privateWalletMigrationFeatureFlag
    )

    beforeTest {
      accountService.setActiveAccount(FullAccountMock)
      bitcoinDisplayPreferenceRepository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Satoshi)
      bip177FeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      privateWalletMigrationFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      coachmarksGlobalFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      bip177CoachmarkEligibilityDao.reset()
      onboardingCompletionService.reset()
      service = CoachmarkServiceImpl(
        CoachmarkDaoImpl(BitkeyDatabaseProviderImpl(sqlDriver.factory)),
        accountService,
        createVisibilityDecider(),
        coachmarksGlobalFlag,
        eventTracker,
        ClockFake(),
        AppVariant.Development
      )
      service.resetCoachmarks()
    }

    test("coachmarksToDisplay") {
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.PrivateWalletHomeCoachmark
          )
        ).shouldBe(
          Ok(
            listOf(
              CoachmarkIdentifier.PrivateWalletHomeCoachmark
            )
          )
        )
    }

    test("didDisplayCoachmark") {
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.PrivateWalletHomeCoachmark))
        .shouldBe(Ok(listOf(CoachmarkIdentifier.PrivateWalletHomeCoachmark)))
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
      eventTracker.eventCalls
        .awaitItem()
        .action
        .shouldBe(Action.ACTION_APP_COACHMARK_VIEWED_PRIVATE_WALLET_HOME)
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.PrivateWalletHomeCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("resetCoachmarks") {
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.PrivateWalletHomeCoachmark
          )
        ).shouldBe(
          Ok(
            listOf(
              CoachmarkIdentifier.PrivateWalletHomeCoachmark
            )
          )
        )
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
      eventTracker.eventCalls
        .awaitItem()
        .action
        .shouldBe(Action.ACTION_APP_COACHMARK_VIEWED_PRIVATE_WALLET_HOME)
      service.resetCoachmarks()
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.PrivateWalletHomeCoachmark
          )
        ).shouldBe(
          Ok(
            listOf(
              CoachmarkIdentifier.PrivateWalletHomeCoachmark
            )
          )
        )
    }

    test("no coachmarks to display for lite accounts") {
      accountService.setActiveAccount(LiteAccountMock)
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.PrivateWalletHomeCoachmark
          )
        ).shouldBe(
          Ok(emptyList())
        )
    }

    test("don't return expired coachmarks") {
      val coachmarkDao = CoachmarkDaoFake()
      coachmarkDao.insertCoachmark(CoachmarkIdentifier.PrivateWalletHomeCoachmark, Instant.DISTANT_PAST)
      service = CoachmarkServiceImpl(
        coachmarkDao,
        accountService,
        createVisibilityDecider(),
        coachmarksGlobalFlag,
        eventTracker,
        ClockFake(),
        AppVariant.Development
      )
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.PrivateWalletHomeCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("don't return viewed coachmarks") {
      val coachmarkDao = CoachmarkDaoFake()
      coachmarkDao
        .insertCoachmark(CoachmarkIdentifier.PrivateWalletHomeCoachmark, Instant.DISTANT_FUTURE)
      coachmarkDao.setViewed(CoachmarkIdentifier.PrivateWalletHomeCoachmark)
      service = CoachmarkServiceImpl(
        coachmarkDao,
        accountService,
        createVisibilityDecider(),
        coachmarksGlobalFlag,
        eventTracker,
        ClockFake(),
        AppVariant.Development
      )
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.PrivateWalletHomeCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("don't return any coachmarks if the global flag is on") {
      coachmarksGlobalFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.PrivateWalletHomeCoachmark
          )
        ).shouldBe(
          Ok(emptyList())
        )
    }

    test("don't return any coachmarks for EEK builds") {
      val eekService = CoachmarkServiceImpl(
        CoachmarkDaoImpl(BitkeyDatabaseProviderImpl(sqlDriver.factory)),
        accountService,
        createVisibilityDecider(),
        coachmarksGlobalFlag,
        eventTracker,
        ClockFake(),
        AppVariant.Emergency
      )
      eekService
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.PrivateWalletHomeCoachmark
          )
        ).shouldBe(
          Ok(emptyList())
        )
    }

    context("BIP 177 feature flag") {

      test("does not create BIP-177 when flag off or unit is BTC") {
        accountService.setActiveAccount(FullAccountMock)
        bitcoinDisplayPreferenceRepository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Bitcoin)
        val eligibilityDao = Bip177CoachmarkEligibilityDaoFake()

        val clock = ClockFake()
        val coachmarkDao = CoachmarkDaoFake()
        val serviceWithFakeDao = CoachmarkServiceImpl(
          coachmarkDao,
          accountService,
          createVisibilityDecider(clock = clock, eligibilityDao = eligibilityDao),
          coachmarksGlobalFlag,
          eventTracker,
          clock,
          AppVariant.Development
        )

        serviceWithFakeDao
          .coachmarksToDisplay(setOf(CoachmarkIdentifier.Bip177Coachmark))
          .shouldBe(Ok(emptyList()))
        coachmarkDao
          .getCoachmark(CoachmarkIdentifier.Bip177Coachmark)
          .shouldBe(Ok(null))
      }

      test("creates and shows BIP-177 when flag on and unit is sats") {
        accountService.setActiveAccount(FullAccountMock)
        bitcoinDisplayPreferenceRepository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Satoshi)
        bip177FeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        val eligibilityDao = Bip177CoachmarkEligibilityDaoFake()

        val clock = ClockFake()
        val coachmarkDao = CoachmarkDaoFake()
        val serviceWithFakeDao = CoachmarkServiceImpl(
          coachmarkDao,
          accountService,
          createVisibilityDecider(clock = clock, eligibilityDao = eligibilityDao),
          coachmarksGlobalFlag,
          eventTracker,
          clock,
          AppVariant.Development
        )

        val expectedExpiration = clock.now() + CoachmarkIdentifier.Bip177Coachmark.expiration!!

        serviceWithFakeDao
          .coachmarksToDisplay(setOf(CoachmarkIdentifier.Bip177Coachmark))
          .shouldBe(Ok(listOf(CoachmarkIdentifier.Bip177Coachmark)))
        coachmarkDao
          .getCoachmark(CoachmarkIdentifier.Bip177Coachmark)
          .shouldBe(
            Ok(
              Coachmark(
                id = CoachmarkIdentifier.Bip177Coachmark,
                viewed = false,
                expiration = expectedExpiration
              )
            )
          )
      }

      test("switching BTC -> sats does not trigger creation when flag was already on") {
        accountService.setActiveAccount(FullAccountMock)
        bitcoinDisplayPreferenceRepository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Bitcoin)
        bip177FeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        val eligibilityDao = Bip177CoachmarkEligibilityDaoFake()

        val clock = ClockFake()
        val coachmarkDao = CoachmarkDaoFake()
        val serviceWithFakeDao = CoachmarkServiceImpl(
          coachmarkDao,
          accountService,
          createVisibilityDecider(clock = clock, eligibilityDao = eligibilityDao),
          coachmarksGlobalFlag,
          eventTracker,
          clock,
          AppVariant.Development
        )

        serviceWithFakeDao
          .coachmarksToDisplay(setOf(CoachmarkIdentifier.Bip177Coachmark))
          .shouldBe(Ok(emptyList()))
        coachmarkDao
          .getCoachmark(CoachmarkIdentifier.Bip177Coachmark)
          .shouldBe(Ok(null))

        bitcoinDisplayPreferenceRepository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Satoshi)

        serviceWithFakeDao
          .coachmarksToDisplay(setOf(CoachmarkIdentifier.Bip177Coachmark))
          .shouldBe(Ok(emptyList()))
        coachmarkDao
          .getCoachmark(CoachmarkIdentifier.Bip177Coachmark)
          .shouldBe(Ok(null))
      }

      test("coachmark without expiration never expires") {
        accountService.setActiveAccount(FullAccountMock)
        bitcoinDisplayPreferenceRepository.setBitcoinDisplayUnit(BitcoinDisplayUnit.Satoshi)
        bip177FeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
        val eligibilityDao = Bip177CoachmarkEligibilityDaoFake()

        val clock = ClockFake()
        val coachmarkDao = CoachmarkDaoFake()
        val serviceWithFakeDao = CoachmarkServiceImpl(
          coachmarkDao,
          accountService,
          createVisibilityDecider(clock = clock, eligibilityDao = eligibilityDao),
          coachmarksGlobalFlag,
          eventTracker,
          clock,
          AppVariant.Development
        )

        serviceWithFakeDao
          .coachmarksToDisplay(setOf(CoachmarkIdentifier.PrivateWalletHomeCoachmark))
          .shouldBe(Ok(listOf(CoachmarkIdentifier.PrivateWalletHomeCoachmark)))

        // Coachmark should still be visible even after a long time since it has no expiration
        clock.advanceBy(15.days)
        serviceWithFakeDao
          .coachmarksToDisplay(setOf(CoachmarkIdentifier.PrivateWalletHomeCoachmark))
          .shouldBe(Ok(listOf(CoachmarkIdentifier.PrivateWalletHomeCoachmark)))
      }
    }

    test("coachmark expiration timer should not start before feature flag is enabled") {
      accountService.setActiveAccount(FullAccountMock)
      bip177FeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

      val clock = ClockFake()
      val coachmarkDao = CoachmarkDaoFake()
      val serviceWithFakeDao = CoachmarkServiceImpl(
        coachmarkDao,
        accountService,
        createVisibilityDecider(clock = clock),
        coachmarksGlobalFlag,
        eventTracker,
        clock,
        AppVariant.Development
      )

      serviceWithFakeDao
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.Bip177Coachmark))
        .shouldBe(Ok(emptyList()))

      coachmarkDao.getCoachmark(CoachmarkIdentifier.Bip177Coachmark)
        .shouldBe(Ok(null))

      clock.advanceBy(15.days)

      bip177FeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

      serviceWithFakeDao
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.Bip177Coachmark))
        .shouldBe(Ok(listOf(CoachmarkIdentifier.Bip177Coachmark)))
    }
  })
