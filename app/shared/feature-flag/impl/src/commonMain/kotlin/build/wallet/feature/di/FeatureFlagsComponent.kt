package build.wallet.feature.di

import build.wallet.di.AppScope
import build.wallet.di.SingleIn
import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.*
import build.wallet.platform.device.DeviceInfoProvider
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo

/**
 * DI components that provides bindings for feature flags.
 *
 * We are implementing providers manually because feature flag types live in the :public module
 * where we don't use DI infrastructure.
 *
 * To add a new feature flag bindings, make sure `@SingleIn` is added to make it singleton since
 * the flags are stateful.
 */
@Suppress("TooManyFunctions")
@ContributesTo(AppScope::class)
interface FeatureFlagsComponent {
  @Provides
  @SingleIn(AppScope::class)
  fun asyncNfcSigningFeatureFlag(featureFlagDao: FeatureFlagDao) =
    AsyncNfcSigningFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun coachmarksGlobalFeatureFlag(featureFlagDao: FeatureFlagDao) =
    CoachmarksGlobalFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun composeUiFeatureFlag(
    featureFlagDao: FeatureFlagDao,
    deviceInfoProvider: DeviceInfoProvider,
  ) = ComposeUiFeatureFlag(featureFlagDao, deviceInfoProvider)

  @Provides
  @SingleIn(AppScope::class)
  fun expectedTransactionsPhase2FeatureFlag(featureFlagDao: FeatureFlagDao) =
    ExpectedTransactionsPhase2FeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun feeBumpIsAvailableFeatureFlag(featureFlagDao: FeatureFlagDao) =
    FeeBumpIsAvailableFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun firmwareCommsLoggingFeatureFlag(featureFlagDao: FeatureFlagDao) =
    FirmwareCommsLoggingFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun inheritanceFeatureFlag(featureFlagDao: FeatureFlagDao) =
    InheritanceFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun nfcHapticsOnConnectedIsEnabledFeatureFlag(featureFlagDao: FeatureFlagDao) =
    NfcHapticsOnConnectedIsEnabledFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun promptSweepFeatureFlag(featureFlagDao: FeatureFlagDao) =
    PromptSweepFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun sellBitcoinMaxAmountFeatureFlag(featureFlagDao: FeatureFlagDao) =
    SellBitcoinMaxAmountFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun sellBitcoinMinAmountFeatureFlag(featureFlagDao: FeatureFlagDao) =
    SellBitcoinMinAmountFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun sellBitcoinQuotesEnabledFeatureFlag(featureFlagDao: FeatureFlagDao) =
    SellBitcoinQuotesEnabledFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun softwareWalletIsEnabledFeatureFlag(featureFlagDao: FeatureFlagDao) =
    SoftwareWalletIsEnabledFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun utxoMaxConsolidationCountFeatureFlag(featureFlagDao: FeatureFlagDao) =
    UtxoMaxConsolidationCountFeatureFlag(featureFlagDao)

  @Provides
  fun featureFlags(
    asyncNfcSigningFeatureFlag: AsyncNfcSigningFeatureFlag,
    coachmarksGlobalFeatureFlag: CoachmarksGlobalFeatureFlag,
    expectedTransactionsPhase2FeatureFlag: ExpectedTransactionsPhase2FeatureFlag,
    feeBumpIsAvailableFeatureFlag: FeeBumpIsAvailableFeatureFlag,
    firmwareCommsLoggingFeatureFlag: FirmwareCommsLoggingFeatureFlag,
    inheritanceFeatureFlag: InheritanceFeatureFlag,
    nfcHapticsOnConnectedIsEnabledFeatureFlag: NfcHapticsOnConnectedIsEnabledFeatureFlag,
    promptSweepFeatureFlag: PromptSweepFeatureFlag,
    sellBitcoinMaxAmountFeatureFlag: SellBitcoinMaxAmountFeatureFlag,
    sellBitcoinMinAmountFeatureFlag: SellBitcoinMinAmountFeatureFlag,
    sellBitcoinQuotesEnabledFeatureFlag: SellBitcoinQuotesEnabledFeatureFlag,
    softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
    utxoMaxConsolidationCountFeatureFlag: UtxoMaxConsolidationCountFeatureFlag,
    composeUiFeatureFlag: ComposeUiFeatureFlag,
  ): List<FeatureFlag<out FeatureFlagValue>> {
    return listOf(
      promptSweepFeatureFlag,
      inheritanceFeatureFlag,
      composeUiFeatureFlag,
      coachmarksGlobalFeatureFlag,
      asyncNfcSigningFeatureFlag,
      utxoMaxConsolidationCountFeatureFlag,
      sellBitcoinQuotesEnabledFeatureFlag,
      feeBumpIsAvailableFeatureFlag,
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      softwareWalletIsEnabledFeatureFlag,
      firmwareCommsLoggingFeatureFlag,
      expectedTransactionsPhase2FeatureFlag,
      sellBitcoinMinAmountFeatureFlag,
      sellBitcoinMaxAmountFeatureFlag
    )
  }
}
