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
  fun transactionNotesFeatureFlag(featureFlagDao: FeatureFlagDao) =
    TransactionNotesFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun transactionNotesSyncFrequencySecondsFeatureFlag(featureFlagDao: FeatureFlagDao) =
    TransactionNotesSyncFrequencySecondsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun configurableDelayNotifyFeatureFlag(featureFlagDao: FeatureFlagDao) =
    ConfigurableDelayNotifyFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun configurableDelayNotifyW3MinFirmwareVersionFeatureFlag(featureFlagDao: FeatureFlagDao) =
    ConfigurableDelayNotifyW3MinFirmwareVersionFeatureFlag(featureFlagDao)

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
  fun sellBitcoinMinAmountFeatureFlag(featureFlagDao: FeatureFlagDao) =
    SellBitcoinMinAmountFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun sellBitcoinMaxAmountFeatureFlag(featureFlagDao: FeatureFlagDao) =
    SellBitcoinMaxAmountFeatureFlag(featureFlagDao)


  @Provides
  @SingleIn(AppScope::class)
  fun provideNfcSessionRetryAttemptsFeatureFlag(featureFlagDao: FeatureFlagDao) =
    NfcSessionRetryAttemptsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun fwupNfcCooldownPeriodSecondsFeatureFlag(featureFlagDao: FeatureFlagDao) =
    FwupNfcCooldownPeriodSecondsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun fwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag(featureFlagDao: FeatureFlagDao) =
    FwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun fingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao: FeatureFlagDao) =
    FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun w3PairingMinFirmwareVersionFeatureFlag(featureFlagDao: FeatureFlagDao) =
    W3PairingMinFirmwareVersionFeatureFlag(featureFlagDao)

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
  fun onboardingCanUseKeyboxKeysetsFeatureFlag(featureFlagDao: FeatureFlagDao) =
    OnboardingCanUseKeyboxKeysetsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun appUpdateModalFeatureFlag(featureFlagDao: FeatureFlagDao) =
    AppUpdateModalFeatureFlag(featureFlagDao)

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
  fun updateToPrivateWalletOnRecoveryFeatureFlag(featureFlagDao: FeatureFlagDao) =
    UpdateToPrivateWalletOnRecoveryFeatureFlag(featureFlagDao)

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
  fun ageRangeVerificationFeatureFlag(
    featureFlagDao: FeatureFlagDao,
    appVariant: AppVariant,
  ) = AgeRangeVerificationFeatureFlag(featureFlagDao, appVariant)

  @Provides
  @SingleIn(AppScope::class)
  fun keysetRepairFeatureFlag(featureFlagDao: FeatureFlagDao) =
    KeysetRepairFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun sweepKeysetReconciliationFeatureFlag(featureFlagDao: FeatureFlagDao) =
    SweepKeysetReconciliationFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun preBuiltPsbtFlowFeatureFlag(featureFlagDao: FeatureFlagDao) =
    PreBuiltPsbtFlowFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun cloudBackupForceReuploadTimestampFeatureFlag(featureFlagDao: FeatureFlagDao) =
    CloudBackupForceReuploadTimestampFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun w3MidUpgradeRecoveryGuardFeatureFlag(featureFlagDao: FeatureFlagDao) =
    W3MidUpgradeRecoveryGuardFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun iosCloudKitBackupFeatureFlag(featureFlagDao: FeatureFlagDao) =
    IosCloudKitBackupFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun vaultsFeatureFlag(featureFlagDao: FeatureFlagDao) = VaultsFeatureFlag(featureFlagDao)

  @Provides
  @SingleIn(AppScope::class)
  fun wipeHardwareLoggedOutFeatureFlag(featureFlagDao: FeatureFlagDao) =
    WipeHardwareLoggedOutFeatureFlag(featureFlagDao)

  @Provides
  fun featureFlags(
    asyncNfcSigningFeatureFlag: AsyncNfcSigningFeatureFlag,
    coachmarksGlobalFeatureFlag: CoachmarksGlobalFeatureFlag,
    expectedTransactionsPhase2FeatureFlag: ExpectedTransactionsPhase2FeatureFlag,
    firmwareCommsLoggingFeatureFlag: FirmwareCommsLoggingFeatureFlag,
    nfcHapticsOnConnectedIsEnabledFeatureFlag: NfcHapticsOnConnectedIsEnabledFeatureFlag,
    softwareWalletIsEnabledFeatureFlag: SoftwareWalletIsEnabledFeatureFlag,
    utxoMaxConsolidationCountFeatureFlag: UtxoMaxConsolidationCountFeatureFlag,
    mobileRealTimeMetricsFeatureFlag: MobileRealTimeMetricsFeatureFlag,
    sellBitcoinMinAmountFeatureFlag: SellBitcoinMinAmountFeatureFlag,
    sellBitcoinMaxAmountFeatureFlag: SellBitcoinMaxAmountFeatureFlag,
    nfcSessionRetryAttemptsFeatureFlag: NfcSessionRetryAttemptsFeatureFlag,
    fwupNfcCooldownPeriodSecondsFeatureFlag: FwupNfcCooldownPeriodSecondsFeatureFlag,
    fwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag:
      FwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag,
    fingerprintResetMinFirmwareVersionFeatureFlag: FingerprintResetMinFirmwareVersionFeatureFlag,
    w3PairingMinFirmwareVersionFeatureFlag: W3PairingMinFirmwareVersionFeatureFlag,
    txVerificationFeatureFlag: TxVerificationFeatureFlag,
    transactionNotesFeatureFlag: TransactionNotesFeatureFlag,
    transactionNotesSyncFrequencySecondsFeatureFlag: TransactionNotesSyncFrequencySecondsFeatureFlag,
    atRiskNotificationsFeatureFlag: AtRiskNotificationsFeatureFlag,
    chaincodeDelegationFeatureFlag: ChaincodeDelegationFeatureFlag,
    configurableDelayNotifyFeatureFlag: ConfigurableDelayNotifyFeatureFlag,
    configurableDelayNotifyW3MinFirmwareVersionFeatureFlag:
      ConfigurableDelayNotifyW3MinFirmwareVersionFeatureFlag,
    onboardingCanUseKeyboxKeysetsFeatureFlag: OnboardingCanUseKeyboxKeysetsFeatureFlag,
    appUpdateModalFeatureFlag: AppUpdateModalFeatureFlag,
    replaceFullWithLiteAccountFeatureFlag: ReplaceFullWithLiteAccountFeatureFlag,
    orphanedKeyRecoveryFeatureFlag: OrphanedKeyRecoveryFeatureFlag,
    updateToPrivateWalletOnRecoveryFeatureFlag: UpdateToPrivateWalletOnRecoveryFeatureFlag,
    sharedCloudBackupsFeatureFlag: SharedCloudBackupsFeatureFlag,
    bdk2FeatureFlag: Bdk2FeatureFlag,
    cashAppFeePromotionFeatureFlag: CashAppFeePromotionFeatureFlag,
    keysetRepairFeatureFlag: KeysetRepairFeatureFlag,
    sweepKeysetReconciliationFeatureFlag: SweepKeysetReconciliationFeatureFlag,
    ageRangeVerificationFeatureFlag: AgeRangeVerificationFeatureFlag,
    preBuiltPsbtFlowFeatureFlag: PreBuiltPsbtFlowFeatureFlag,
    cloudBackupForceReuploadTimestampFeatureFlag: CloudBackupForceReuploadTimestampFeatureFlag,
    w3MidUpgradeRecoveryGuardFeatureFlag: W3MidUpgradeRecoveryGuardFeatureFlag,
    iosCloudKitBackupFeatureFlag: IosCloudKitBackupFeatureFlag,
    vaultsFeatureFlag: VaultsFeatureFlag,
    wipeHardwareLoggedOutFeatureFlag: WipeHardwareLoggedOutFeatureFlag,
  ): List<FeatureFlag<out FeatureFlagValue>> {
    return listOf(
      bdk2FeatureFlag,
      preBuiltPsbtFlowFeatureFlag,
      softwareWalletIsEnabledFeatureFlag,
      expectedTransactionsPhase2FeatureFlag,
      mobileRealTimeMetricsFeatureFlag,
      sellBitcoinMinAmountFeatureFlag,
      sellBitcoinMaxAmountFeatureFlag,
      fingerprintResetMinFirmwareVersionFeatureFlag,
      w3PairingMinFirmwareVersionFeatureFlag,
      atRiskNotificationsFeatureFlag,
      chaincodeDelegationFeatureFlag,
      configurableDelayNotifyFeatureFlag,
      configurableDelayNotifyW3MinFirmwareVersionFeatureFlag,
      onboardingCanUseKeyboxKeysetsFeatureFlag,
      w3MidUpgradeRecoveryGuardFeatureFlag,
      txVerificationFeatureFlag,
      transactionNotesFeatureFlag,
      transactionNotesSyncFrequencySecondsFeatureFlag,
      appUpdateModalFeatureFlag,
      replaceFullWithLiteAccountFeatureFlag,
      orphanedKeyRecoveryFeatureFlag,
      updateToPrivateWalletOnRecoveryFeatureFlag,
      sharedCloudBackupsFeatureFlag,
      cashAppFeePromotionFeatureFlag,
      keysetRepairFeatureFlag,
      sweepKeysetReconciliationFeatureFlag,
      ageRangeVerificationFeatureFlag,
      iosCloudKitBackupFeatureFlag,
      utxoMaxConsolidationCountFeatureFlag,
      coachmarksGlobalFeatureFlag,
      nfcHapticsOnConnectedIsEnabledFeatureFlag,
      nfcSessionRetryAttemptsFeatureFlag,
      fwupNfcCooldownPeriodSecondsFeatureFlag,
      fwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag,
      firmwareCommsLoggingFeatureFlag,
      asyncNfcSigningFeatureFlag,
      cloudBackupForceReuploadTimestampFeatureFlag,
      vaultsFeatureFlag,
      wipeHardwareLoggedOutFeatureFlag
    )
  }
}
