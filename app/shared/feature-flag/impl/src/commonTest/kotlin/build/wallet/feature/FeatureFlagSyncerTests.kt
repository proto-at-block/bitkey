package build.wallet.feature

import build.wallet.account.AccountRepositoryFake
import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.debug.DebugOptionsServiceFake
import build.wallet.f8e.featureflags.F8eFeatureFlagValue
import build.wallet.f8e.featureflags.FeatureFlagsF8eClient
import build.wallet.f8e.featureflags.FeatureFlagsF8eClientMock
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class FeatureFlagSyncerTests : FunSpec({
  coroutineTestScope = true

  class LocalBooleanFeatureFlag(
    featureFlagDao: FeatureFlagDao,
  ) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
      identifier = "silly-mode-enabled",
      title = "Silly Mode Enabled",
      description = "Controls whether or not the app is silly.",
      defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
      featureFlagDao = featureFlagDao,
      type = FeatureFlagValue.BooleanFlag::class
    )

  fun remoteBooleanFeatureFlag(value: Boolean) =
    FeatureFlagsF8eClient.F8eFeatureFlag(
      key = "silly-mode-enabled",
      value = F8eFeatureFlagValue.BooleanValue(value)
    )

  val getFeatureFlagsF8eClient = FeatureFlagsF8eClientMock(
    featureFlags = listOf(remoteBooleanFeatureFlag(false)),
    turbine = turbines::create
  )

  val clock = ClockFake()

  val sqlDriver = inMemorySqlDriver()

  val appSessionManager = AppSessionManagerFake()
  val debugOptionsService = DebugOptionsServiceFake()

  lateinit var featureFlagDao: FeatureFlagDao

  lateinit var testFlag: LocalBooleanFeatureFlag

  lateinit var featureFlagSyncer: FeatureFlagSyncerImpl

  suspend fun syncFlags(remoteFlags: List<FeatureFlagsF8eClient.F8eFeatureFlag>) {
    getFeatureFlagsF8eClient.setFlags(remoteFlags)
    featureFlagSyncer.sync()
  }

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    featureFlagDao = FeatureFlagDaoImpl(databaseProvider)

    testFlag = LocalBooleanFeatureFlag(featureFlagDao)
    testFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false), overridden = false)

    appSessionManager.reset()
    debugOptionsService.reset()

    featureFlagSyncer = FeatureFlagSyncerImpl(
      accountRepository = AccountRepositoryFake(),
      debugOptionsService = debugOptionsService,
      featureFlagsF8eClient = getFeatureFlagsF8eClient,
      clock = clock,
      remoteFlags = listOf(testFlag),
      appSessionManager = appSessionManager
    )
  }

  suspend fun assertGetFeatureFlagsCalls() {
    getFeatureFlagsF8eClient.getFeatureFlagsCalls.awaitItem().shouldBe(Unit)
  }

  test("sync flag from false to true") {
    featureFlagSyncer.initializeSyncLoop(backgroundScope)

    testFlag.flagValue().value.value.shouldBe(false)

    syncFlags(
      remoteFlags = listOf(remoteBooleanFeatureFlag(true))
    )

    assertGetFeatureFlagsCalls()

    testFlag.flagValue().value.value.shouldBe(true)
  }

  test("sync flag from false to false") {
    featureFlagSyncer.initializeSyncLoop(backgroundScope)

    testFlag.flagValue().value.value.shouldBe(false)

    syncFlags(
      remoteFlags = listOf(remoteBooleanFeatureFlag(false))
    )
    assertGetFeatureFlagsCalls()

    testFlag.flagValue().value.value.shouldBe(false)
  }

  test("flag doesn't change when missing in remote flags") {
    featureFlagSyncer.initializeSyncLoop(backgroundScope)

    testFlag.flagValue().value.value.shouldBe(false)
    syncFlags(emptyList())
    assertGetFeatureFlagsCalls()
    testFlag.flagValue().value.value.shouldBe(false)

    testFlag.setFlagValue(true)
    testFlag.flagValue().value.value.shouldBe(true)
    syncFlags(emptyList())
    assertGetFeatureFlagsCalls()
    testFlag.flagValue().value.value.shouldBe(true)
  }

  test("syncing respects overridden state") {
    featureFlagSyncer.initializeSyncLoop(backgroundScope)

    testFlag.flagValue().value.value.shouldBe(false)
    testFlag.setOverridden(true)

    syncFlags(
      remoteFlags = listOf(remoteBooleanFeatureFlag(true))
    )
    assertGetFeatureFlagsCalls()

    testFlag.flagValue().value.value.shouldBe(false)
  }

  test("reset returns to default value and clears override") {
    featureFlagSyncer.initializeSyncLoop(backgroundScope)

    testFlag.flagValue().value.value.shouldBe(false)
    testFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true), overridden = true)
    testFlag.flagValue().value.value.shouldBe(true)
    testFlag.isOverridden().shouldBe(true)

    testFlag.reset()
    testFlag.flagValue().value.value.shouldBe(false)
    testFlag.isOverridden().shouldBe(false)
  }

  // This test is tricky because I can't get the session flow to update
  // I'll revisit in a fast follow
  xtest("applicationDidEnterForeground will only sync after launch and at a maximum frequency of every 5 seconds") {
    featureFlagSyncer.initializeSyncLoop(backgroundScope)

    testFlag.flagValue().value.value.shouldBe(false)

    // Perform initial sync
    featureFlagSyncer.sync()
    testFlag.flagValue().value.value.shouldBe(true)
    assertGetFeatureFlagsCalls()

    // Set flag to false and immediately get an [applicationDidEnterForeground] call.
    // Should not sync as not enough time has passed.
    getFeatureFlagsF8eClient.setFlags(listOf(remoteBooleanFeatureFlag(false)))
    testFlag.flagValue().value.value.shouldBe(true)
    getFeatureFlagsF8eClient.getFeatureFlagsCalls.expectNoEvents()

    clock.advanceBy(6.seconds)

    // Enough time has passed, the sync should execute.
    testFlag.flagValue().value.value.shouldBe(false)
    assertGetFeatureFlagsCalls()

    // Attempt a sync again. Not enough time has passed so the sync will not be performed.
    getFeatureFlagsF8eClient.setFlags(listOf(remoteBooleanFeatureFlag(true)))
    testFlag.flagValue().value.value.shouldBe(false)
    getFeatureFlagsF8eClient.getFeatureFlagsCalls.expectNoEvents()
  }
})
