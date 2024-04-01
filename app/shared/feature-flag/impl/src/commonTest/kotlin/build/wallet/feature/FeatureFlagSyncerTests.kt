package build.wallet.feature

import build.wallet.account.AccountRepositoryFake
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.featureflags.F8eFeatureFlagValue
import build.wallet.f8e.featureflags.GetFeatureFlagsService
import build.wallet.f8e.featureflags.GetFeatureFlagsServiceMock
import build.wallet.keybox.config.TemplateFullAccountConfigDaoFake
import build.wallet.sqldelight.inMemorySqlDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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
    GetFeatureFlagsService.F8eFeatureFlag(
      key = "silly-mode-enabled",
      value = F8eFeatureFlagValue.BooleanValue(value)
    )

  val sqlDriver = inMemorySqlDriver()

  lateinit var featureFlagDao: FeatureFlagDao

  lateinit var testFlag: LocalBooleanFeatureFlag

  suspend fun syncFlags(remoteFlags: List<GetFeatureFlagsService.F8eFeatureFlag>) {
    val featureFlagSyncer = FeatureFlagSyncerImpl(
      accountRepository = AccountRepositoryFake(),
      templateFullAccountConfigDao = TemplateFullAccountConfigDaoFake(),
      getFeatureFlagsService = GetFeatureFlagsServiceMock(remoteFlags),
      booleanFlags = listOf(testFlag)
    )
    featureFlagSyncer.sync()
  }

  beforeTest {
    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)
    featureFlagDao = FeatureFlagDaoImpl(databaseProvider)

    testFlag = LocalBooleanFeatureFlag(featureFlagDao)
    testFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false), overridden = false)
  }

  test("sync flag from false to true") {
    testFlag.flagValue().value.value.shouldBe(false)

    syncFlags(
      remoteFlags = listOf(remoteBooleanFeatureFlag(true))
    )

    testFlag.flagValue().value.value.shouldBe(true)
  }

  test("sync flag from false to false") {
    testFlag.flagValue().value.value.shouldBe(false)

    syncFlags(
      remoteFlags = listOf(remoteBooleanFeatureFlag(false))
    )

    testFlag.flagValue().value.value.shouldBe(false)
  }

  test("flag doesn't change when missing in remote flags") {
    testFlag.flagValue().value.value.shouldBe(false)
    syncFlags(emptyList())
    testFlag.flagValue().value.value.shouldBe(false)

    testFlag.setFlagValue(true)
    testFlag.flagValue().value.value.shouldBe(true)
    syncFlags(emptyList())
    testFlag.flagValue().value.value.shouldBe(true)
  }

  test("syncing respects overridden state") {
    testFlag.flagValue().value.value.shouldBe(false)
    testFlag.setOverridden(true)

    syncFlags(
      remoteFlags = listOf(remoteBooleanFeatureFlag(true))
    )

    testFlag.flagValue().value.value.shouldBe(false)
  }

  test("reset returns to default value and clears override") {
    testFlag.flagValue().value.value.shouldBe(false)
    testFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true), overridden = true)
    testFlag.flagValue().value.value.shouldBe(true)
    testFlag.isOverridden().shouldBe(true)

    testFlag.reset()
    testFlag.flagValue().value.value.shouldBe(false)
    testFlag.isOverridden().shouldBe(false)
  }
})
