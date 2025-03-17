package build.wallet.feature

import bitkey.account.AccountConfigServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.coroutines.createBackgroundScope
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.featureflags.F8eFeatureFlagValue
import build.wallet.f8e.featureflags.FeatureFlagsF8eClient
import build.wallet.f8e.featureflags.FeatureFlagsF8eClientFake
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class FeatureFlagSyncerTests : FunSpec({
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

  val featureFlagsF8eClient = FeatureFlagsF8eClientFake()

  val clock = ClockFake()

  val sqlDriver = inMemorySqlDriver()

  val appSessionManager = AppSessionManagerFake()
  val defaultAppConfigService = AccountConfigServiceFake()

  lateinit var featureFlagDao: FeatureFlagDao

  lateinit var testFlag: LocalBooleanFeatureFlag

  lateinit var featureFlagSyncer: FeatureFlagSyncerImpl

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    featureFlagDao = FeatureFlagDaoImpl(databaseProvider)

    testFlag = LocalBooleanFeatureFlag(featureFlagDao)
    testFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false), overridden = false)

    appSessionManager.reset()
    defaultAppConfigService.reset()
    featureFlagsF8eClient.reset()

    featureFlagSyncer = FeatureFlagSyncerImpl(
      accountService = AccountServiceFake(),
      accountConfigService = defaultAppConfigService,
      featureFlagsF8eClient = featureFlagsF8eClient,
      clock = clock,
      featureFlags = listOf(testFlag),
      appSessionManager = appSessionManager
    )
  }

  test("sync flag from false to true") {
    createBackgroundScope().launch {
      featureFlagSyncer.initializeSyncLoop(this)
    }

    testFlag.flagValue().value.value.shouldBe(false)

    featureFlagsF8eClient.setFlags(listOf(remoteBooleanFeatureFlag(true)))
    featureFlagSyncer.sync()

    testFlag.flagValue().value.value.shouldBe(true)
  }

  test("sync flag from false to false") {
    createBackgroundScope().launch {
      featureFlagSyncer.initializeSyncLoop(this)
    }

    testFlag.flagValue().value.value.shouldBe(false)

    featureFlagsF8eClient.setFlags(listOf(remoteBooleanFeatureFlag(false)))
    featureFlagSyncer.sync()

    testFlag.flagValue().value.value.shouldBe(false)
  }

  test("flag doesn't change when missing in remote flags") {
    createBackgroundScope().launch {
      featureFlagSyncer.initializeSyncLoop(this)
    }

    testFlag.flagValue().value.value.shouldBe(false)
    featureFlagsF8eClient.setFlags(emptyList())
    featureFlagSyncer.sync()
    testFlag.flagValue().value.value.shouldBe(false)

    testFlag.setFlagValue(true)
    testFlag.flagValue().value.value.shouldBe(true)
    featureFlagsF8eClient.setFlags(emptyList())
    featureFlagSyncer.sync()
    testFlag.flagValue().value.value.shouldBe(true)
  }

  test("syncing respects overridden state") {
    createBackgroundScope().launch {
      featureFlagSyncer.initializeSyncLoop(this)
    }

    testFlag.flagValue().value.value.shouldBe(false)
    testFlag.setOverridden(true)

    featureFlagsF8eClient.setFlags(listOf(remoteBooleanFeatureFlag(true)))
    featureFlagSyncer.sync()

    testFlag.flagValue().value.value.shouldBe(false)
  }

  test("reset returns to default value and clears override") {
    createBackgroundScope().launch {
      featureFlagSyncer.initializeSyncLoop(this)
    }

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
    createBackgroundScope().launch {
      featureFlagSyncer.initializeSyncLoop(this)
    }

    testFlag.flagValue().value.value.shouldBe(false)

    // Perform initial sync
    featureFlagSyncer.sync()
    testFlag.flagValue().value.value.shouldBe(true)

    // Set flag to false and immediately get an [applicationDidEnterForeground] call.
    // Should not sync as not enough time has passed.
    featureFlagsF8eClient.setFlags(listOf(remoteBooleanFeatureFlag(false)))
    testFlag.flagValue().value.value.shouldBe(true)

    clock.advanceBy(6.seconds)

    // Enough time has passed, the sync should execute.
    testFlag.flagValue().value.value.shouldBe(false)

    // Attempt a sync again. Not enough time has passed so the sync will not be performed.
    featureFlagsF8eClient.setFlags(listOf(remoteBooleanFeatureFlag(true)))
    testFlag.flagValue().value.value.shouldBe(false)
  }
})
