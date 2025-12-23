package build.wallet.feature.di

import build.wallet.di.AppScope
import build.wallet.di.SingleIn
import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.*
import build.wallet.platform.config.AppVariant
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
  @SingleIn(AppScope::class)
  fun descriptorBackupFailsafeFeatureFlag(featureFlagDao: FeatureFlagDao) =
    DescriptorBackupFailsafeFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun updateToPrivateWalletOnRecoveryFeatureFlag(featureFlagDao: FeatureFlagDao) =
    UpdateToPrivateWalletOnRecoveryFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun privateWalletMigrationBalanceThresholdFeatureFlag(featureFlagDao: FeatureFlagDao) =
    PrivateWalletMigrationBalanceThresholdFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun publicCustomerSupportFeatureFlag(featureFlagDao: FeatureFlagDao) =
    PublicCustomerSupportFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun sharedCloudBackupsFeatureFlagFeatureFlag(featureFlagDao: FeatureFlagDao) =
    SharedCloudBackupsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun cashAppFeePromotionFeatureFlag(featureFlagDao: FeatureFlagDao) =
    CashAppFeePromotionFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun bdk2FeatureFlag(featureFlagDao: FeatureFlagDao) = Bdk2FeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun bip177FeatureFlag(featureFlagDao: FeatureFlagDao) = Bip177FeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun ageRangeVerificationFeatureFlag(
    featureFlagDao: FeatureFlagDao,
    appVariant: AppVariant,
  ) = AgeRangeVerificationFeatureFlag(featureFlagDao, appVariant)


  @Provides
  @SingleIn(AppScope::class)
  fun cloudBackupHealthLoggingFeatureFlag(featureFlagDao: FeatureFlagDao) =
    CloudBackupHealthLoggingFeatureFlag(featureFlagDao)

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
    encryptedDescriptorSupportUploadFeatureFlag: EncryptedDescriptorSupportUploadFeatureFlag,
    atRiskNotificationsFeatureFlag: AtRiskNotificationsFeatureFlag,
    chaincodeDelegationFeatureFlag: ChaincodeDelegationFeatureFlag,
    onboardingCompletionFailsafeFeatureFlag: OnboardingCompletionFailsafeFeatureFlag,
    appUpdateModalFeatureFlag: AppUpdateModalFeatureFlag,
    privateWalletMigrationFeatureFlag: PrivateWalletMigrationFeatureFlag,
    replaceFullWithLiteAccountFeatureFlag: ReplaceFullWithLiteAccountFeatureFlag,
    orphanedKeyRecoveryFeatureFlag: OrphanedKeyRecoveryFeatureFlag,
    descriptorBackupFailsafeFeatureFlag: DescriptorBackupFailsafeFeatureFlag,
    updateToPrivateWalletOnRecoveryFeatureFlag: UpdateToPrivateWalletOnRecoveryFeatureFlag,
    privateWalletMigrationBalanceThresholdFeatureFlag:
      PrivateWalletMigrationBalanceThresholdFeatureFlag,
    publicCustomerSupportFeatureFlag: PublicCustomerSupportFeatureFlag,
    sharedCloudBackupsFeatureFlag: SharedCloudBackupsFeatureFlag,
    bdk2FeatureFlag: Bdk2FeatureFlag,
    cashAppFeePromotionFeatureFlag: CashAppFeePromotionFeatureFlag,
    bip177FeatureFlag: Bip177FeatureFlag,
    cloudBackupHealthLoggingFeatureFlag: CloudBackupHealthLoggingFeatureFlag,
    ageRangeVerificationFeatureFlag: AgeRangeVerificationFeatureFlag,
  ): List<FeatureFlag<out FeatureFlagValue>> {
    return listOf(
      bdk2FeatureFlag,
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
      encryptedDescriptorSupportUploadFeatureFlag,
      appUpdateModalFeatureFlag,
      privateWalletMigrationFeatureFlag,
      replaceFullWithLiteAccountFeatureFlag,
      orphanedKeyRecoveryFeatureFlag,
      descriptorBackupFailsafeFeatureFlag,
      updateToPrivateWalletOnRecoveryFeatureFlag,
      privateWalletMigrationBalanceThresholdFeatureFlag,
      publicCustomerSupportFeatureFlag,
      sharedCloudBackupsFeatureFlag,
      cashAppFeePromotionFeatureFlag,
      ageRangeVerificationFeatureFlag,
      // these are long-lived feature flags that are not for actively developing features
      // pushing towards the bottom
      utxoMaxConsolidationCountFeatureFlag,
      sellBitcoinMinAmountFeatureFlag,
      sellBitcoinMaxAmountFeatureFlag,
      coachmarksGlobalFeatureFlag,
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      firmwareCommsLoggingFeatureFlag,
      asyncNfcSigningFeatureFlag,
      bip177FeatureFlag,
      cloudBackupHealthLoggingFeatureFlag,
    )
  }
}
