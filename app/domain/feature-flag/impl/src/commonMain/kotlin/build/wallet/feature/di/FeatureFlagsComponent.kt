package build.wallet.feature.di

import build.wallet.di.AppScope
import build.wallet.di.SingleIn
import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.*
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
  fun expectedTransactionsPhase2FeatureFlag(featureFlagDao: FeatureFlagDao) =
    ExpectedTransactionsPhase2FeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun firmwareCommsLoggingFeatureFlag(featureFlagDao: FeatureFlagDao) =
    FirmwareCommsLoggingFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun nfcHapticsOnConnectedIsEnabledFeatureFlag(featureFlagDao: FeatureFlagDao) =
    NfcHapticsOnConnectedIsEnabledFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun securityHubFeatureFlag(featureFlagDao: FeatureFlagDao) =
    SecurityHubFeatureFlag(featureFlagDao)

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
  fun softwareWalletIsEnabledFeatureFlag(featureFlagDao: FeatureFlagDao) =
    SoftwareWalletIsEnabledFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun provideWipeHardwareLoggedOutFeatureFlag(featureFlagDao: FeatureFlagDao) =
    WipeHardwareLoggedOutFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun utxoMaxConsolidationCountFeatureFlag(featureFlagDao: FeatureFlagDao) =
    UtxoMaxConsolidationCountFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun mobileRealTimeMetricsFeatureFlag(featureFlagDao: FeatureFlagDao) =
    MobileRealTimeMetricsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun provideBalanceHistoryFeatureFlag(featureFlagDao: FeatureFlagDao) =
    BalanceHistoryFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun usSmsFeatureFlag(featureFlagDao: FeatureFlagDao) = UsSmsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun provideCheckHardwareIsPairedFeatureFlag(featureFlagDao: FeatureFlagDao) =
    CheckHardwareIsPairedFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun fingerprintResetFeatureFlag(featureFlagDao: FeatureFlagDao) =
    FingerprintResetFeatureFlag(featureFlagDao)

  @Provides
  fun featureFlags(
    asyncNfcSigningFeatureFlag: AsyncNfcSigningFeatureFlag,
    coachmarksGlobalFeatureFlag: CoachmarksGlobalFeatureFlag,
    expectedTransactionsPhase2FeatureFlag: ExpectedTransactionsPhase2FeatureFlag,
    firmwareCommsLoggingFeatureFlag: FirmwareCommsLoggingFeatureFlag,
    nfcHapticsOnConnectedIsEnabledFeatureFlag: NfcHapticsOnConnectedIsEnabledFeatureFlag,
    sellBitcoinMaxAmountFeatureFlag: SellBitcoinMaxAmountFeatureFlag,
    sellBitcoinMinAmountFeatureFlag: SellBitcoinMinAmountFeatureFlag,
    softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
    utxoMaxConsolidationCountFeatureFlag: UtxoMaxConsolidationCountFeatureFlag,
    mobileRealTimeMetricsFeatureFlag: MobileRealTimeMetricsFeatureFlag,
    securityHubFeatureFlag: SecurityHubFeatureFlag,
    balanceHistoryFeatureFlag: BalanceHistoryFeatureFlag,
    wipeHardwareLoggedOutFeatureFlag: WipeHardwareLoggedOutFeatureFlag,
    usSmsFeatureFlag: UsSmsFeatureFlag,
    checkHardwareIsPairedFeatureFlag: CheckHardwareIsPairedFeatureFlag,
    fingerprintResetFeatureFlag: FingerprintResetFeatureFlag,
  ): List<FeatureFlag<out FeatureFlagValue>> {
    return listOf(
      balanceHistoryFeatureFlag,
      softwareWalletIsEnabledFeatureFlag,
      expectedTransactionsPhase2FeatureFlag,
      mobileRealTimeMetricsFeatureFlag,
      securityHubFeatureFlag,
      usSmsFeatureFlag,
      checkHardwareIsPairedFeatureFlag,
      fingerprintResetFeatureFlag,
      // these are long-lived feature flags that are not for actively developing features
      // pushing towards the bottom
      utxoMaxConsolidationCountFeatureFlag,
      sellBitcoinMinAmountFeatureFlag,
      sellBitcoinMaxAmountFeatureFlag,
      coachmarksGlobalFeatureFlag,
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      firmwareCommsLoggingFeatureFlag,
      wipeHardwareLoggedOutFeatureFlag,
      asyncNfcSigningFeatureFlag
    )
  }
}
