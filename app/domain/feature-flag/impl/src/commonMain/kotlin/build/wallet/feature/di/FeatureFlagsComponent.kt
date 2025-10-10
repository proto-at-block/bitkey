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
  fun transactionVerificationFlag(featureFlagDao: FeatureFlagDao) =
    TxVerificationFeatureFlag(featureFlagDao)

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
  fun utxoMaxConsolidationCountFeatureFlag(featureFlagDao: FeatureFlagDao) =
    UtxoMaxConsolidationCountFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun mobileRealTimeMetricsFeatureFlag(featureFlagDao: FeatureFlagDao) =
    MobileRealTimeMetricsFeatureFlag(featureFlagDao)

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
  @SingleIn(AppScope::class)
  fun onboardingCompletionFailsafeFeatureFlag(featureFlagDao: FeatureFlagDao) =
    OnboardingCompletionFailsafeFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun fingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao: FeatureFlagDao) =
    FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun inheritanceUseEncryptedDescriptorFeatureFlag(featureFlagDao: FeatureFlagDao) =
    InheritanceUseEncryptedDescriptorFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun encryptedDescriptorBackupsFeatureFlag(featureFlagDao: FeatureFlagDao) =
    EncryptedDescriptorBackupsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun encryptedDescriptorUploadFeatureFlag(featureFlagDao: FeatureFlagDao) =
    EncryptedDescriptorSupportUploadFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun atRiskNotificationsFeatureFlag(featureFlagDao: FeatureFlagDao) =
    AtRiskNotificationsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun chaincodeDelegationFeatureFlag(featureFlagDao: FeatureFlagDao) =
    ChaincodeDelegationFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun receiveV2ScreenFeatureFlag(featureFlagDao: FeatureFlagDao) =
    ReceiveV2ScreenFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun appUpdateModalFeatureFlag(featureFlagDao: FeatureFlagDao) =
    AppUpdateModalFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun privateWalletMigrationFeatureFlag(featureFlagDao: FeatureFlagDao) =
    PrivateWalletMigrationFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun replaceFullWithLiteAccountFeatureFlag(featureFlagDao: FeatureFlagDao) =
    ReplaceFullWithLiteAccountFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun orphanedKeyRecoveryFeatureFlag(featureFlagDao: FeatureFlagDao) =
    OrphanedKeyRecoveryFeatureFlag(featureFlagDao)

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
    usSmsFeatureFlag: UsSmsFeatureFlag,
    checkHardwareIsPairedFeatureFlag: CheckHardwareIsPairedFeatureFlag,
    fingerprintResetFeatureFlag: FingerprintResetFeatureFlag,
    fingerprintResetMinFirmwareVersionFeatureFlag: FingerprintResetMinFirmwareVersionFeatureFlag,
    txVerificationFeatureFlag: TxVerificationFeatureFlag,
    inheritanceUseEncryptedDescriptorFeatureFlag: InheritanceUseEncryptedDescriptorFeatureFlag,
    encryptedDescriptorBackupsFeatureFlag: EncryptedDescriptorBackupsFeatureFlag,
    encryptedDescriptorSupportUploadFeatureFlag: EncryptedDescriptorSupportUploadFeatureFlag,
    atRiskNotificationsFeatureFlag: AtRiskNotificationsFeatureFlag,
    chaincodeDelegationFeatureFlag: ChaincodeDelegationFeatureFlag,
    onboardingCompletionFailsafeFeatureFlag: OnboardingCompletionFailsafeFeatureFlag,
    receiveV2ScreenFeatureFlag: ReceiveV2ScreenFeatureFlag,
    appUpdateModalFeatureFlag: AppUpdateModalFeatureFlag,
    privateWalletMigrationFeatureFlag: PrivateWalletMigrationFeatureFlag,
    replaceFullWithLiteAccountFeatureFlag: ReplaceFullWithLiteAccountFeatureFlag,
    orphanedKeyRecoveryFeatureFlag: OrphanedKeyRecoveryFeatureFlag,
  ): List<FeatureFlag<out FeatureFlagValue>> {
    return listOf(
      softwareWalletIsEnabledFeatureFlag,
      expectedTransactionsPhase2FeatureFlag,
      mobileRealTimeMetricsFeatureFlag,
      usSmsFeatureFlag,
      checkHardwareIsPairedFeatureFlag,
      fingerprintResetFeatureFlag,
      fingerprintResetMinFirmwareVersionFeatureFlag,
      atRiskNotificationsFeatureFlag,
      chaincodeDelegationFeatureFlag,
      onboardingCompletionFailsafeFeatureFlag,
      txVerificationFeatureFlag,
      inheritanceUseEncryptedDescriptorFeatureFlag,
      encryptedDescriptorBackupsFeatureFlag,
      encryptedDescriptorSupportUploadFeatureFlag,
      receiveV2ScreenFeatureFlag,
      appUpdateModalFeatureFlag,
      privateWalletMigrationFeatureFlag,
      replaceFullWithLiteAccountFeatureFlag,
      orphanedKeyRecoveryFeatureFlag,
      // these are long-lived feature flags that are not for actively developing features
      // pushing towards the bottom
      utxoMaxConsolidationCountFeatureFlag,
      sellBitcoinMinAmountFeatureFlag,
      sellBitcoinMaxAmountFeatureFlag,
      coachmarksGlobalFeatureFlag,
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      firmwareCommsLoggingFeatureFlag,
      asyncNfcSigningFeatureFlag
    )
  }
}
