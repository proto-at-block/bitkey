package build.wallet.database

import app.cash.sqldelight.EnumColumnAdapter
import build.wallet.analytics.v1.Event
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.bitkey.socrec.TrustedContactEnrollmentPakeKey
import build.wallet.crypto.CurveType
import build.wallet.database.adapters.BitcoinAddressColumnAdapter
import build.wallet.database.adapters.DurationColumnAdapter
import build.wallet.database.adapters.EmailColumnAdapter
import build.wallet.database.adapters.FullAccountColumnAdapter
import build.wallet.database.adapters.InactiveKeysetIdsColumnAdapter
import build.wallet.database.adapters.IsoCurrencyTextCodeColumnAdapter
import build.wallet.database.adapters.MobilePaySnapValueColumnAdapter
import build.wallet.database.adapters.NetworkConnectionColumnAdapter
import build.wallet.database.adapters.ProtectedCustomerAliasColumnAdapter
import build.wallet.database.adapters.PublicKeyColumnAdapter
import build.wallet.database.adapters.SocRecKeyColumnAdapter
import build.wallet.database.adapters.TimeZoneColumnAdapter
import build.wallet.database.adapters.TrustedContactAliasColumnAdapter
import build.wallet.database.adapters.bitkey.AppGlobalAuthKeyHwSignatureColumnAdapter
import build.wallet.database.adapters.bitkey.AppGlobalAuthPublicKeyColumnAdapter
import build.wallet.database.adapters.bitkey.AppRecoveryAuthPublicKeyColumnAdapter
import build.wallet.database.adapters.bitkey.AppSpendingPublicKeyColumnAdapter
import build.wallet.database.adapters.bitkey.F8eEnvironmentColumnAdapter
import build.wallet.database.adapters.bitkey.F8eSpendingPublicKeyColumnAdapter
import build.wallet.database.adapters.bitkey.FullAccountIdColumnAdapter
import build.wallet.database.adapters.bitkey.HwAuthPublicKeyColumnAdapter
import build.wallet.database.adapters.bitkey.HwSpendingPublicKeyColumnAdapter
import build.wallet.database.adapters.bitkey.LiteAccountIdColumnAdapter
import build.wallet.database.sqldelight.ActiveFullAccountEntity
import build.wallet.database.sqldelight.ActiveLiteAccountEntity
import build.wallet.database.sqldelight.ActiveServerRecoveryEntity
import build.wallet.database.sqldelight.AppKeyBundleEntity
import build.wallet.database.sqldelight.AuthKeyRotationAttemptEntity
import build.wallet.database.sqldelight.BitcoinDisplayPreferenceEntity
import build.wallet.database.sqldelight.BitkeyDatabase
import build.wallet.database.sqldelight.BitkeyDebugDatabase
import build.wallet.database.sqldelight.EmailTouchpointEntity
import build.wallet.database.sqldelight.EventEntity
import build.wallet.database.sqldelight.ExchangeRateEntity
import build.wallet.database.sqldelight.FiatCurrencyEntity
import build.wallet.database.sqldelight.FiatCurrencyMobilePayConfigurationEntity
import build.wallet.database.sqldelight.FiatCurrencyPreferenceEntity
import build.wallet.database.sqldelight.FirmwareDeviceInfoEntity
import build.wallet.database.sqldelight.FullAccountEntity
import build.wallet.database.sqldelight.FwupDataEntity
import build.wallet.database.sqldelight.GettingStartedTaskEntity
import build.wallet.database.sqldelight.HistoricalExchangeRateEntity
import build.wallet.database.sqldelight.HomeUiBottomSheetEntity
import build.wallet.database.sqldelight.HwKeyBundleEntity
import build.wallet.database.sqldelight.KeyboxEntity
import build.wallet.database.sqldelight.LiteAccountEntity
import build.wallet.database.sqldelight.LocalRecoveryAttemptEntity
import build.wallet.database.sqldelight.NetworkReachabilityEventEntity
import build.wallet.database.sqldelight.OnboardingFullAccountEntity
import build.wallet.database.sqldelight.OnboardingKeyboxHwAuthPublicKey
import build.wallet.database.sqldelight.OnboardingLiteAccountEntity
import build.wallet.database.sqldelight.OnboardingStepSkipConfigEntity
import build.wallet.database.sqldelight.OnboardingStepStateEntity
import build.wallet.database.sqldelight.PriorityPreferenceEntity
import build.wallet.database.sqldelight.RegisterWatchAddressEntity
import build.wallet.database.sqldelight.SocRecEnrollmentAuthentication
import build.wallet.database.sqldelight.SocRecKeys
import build.wallet.database.sqldelight.SocRecProtectedCustomerEntity
import build.wallet.database.sqldelight.SocRecStartedChallengeAuthentication
import build.wallet.database.sqldelight.SocRecTrustedContactEntity
import build.wallet.database.sqldelight.SocRecTrustedContactInvitationEntity
import build.wallet.database.sqldelight.SocRecUnendorsedTrustedContactEntity
import build.wallet.database.sqldelight.SpendingKeysetEntity
import build.wallet.database.sqldelight.SpendingLimitEntity
import build.wallet.database.sqldelight.TemplateFullAccountConfigEntity
import build.wallet.database.sqldelight.TransactionDetailEntity
import build.wallet.sqldelight.SqlDriverFactory
import build.wallet.sqldelight.adapter.ByteStringColumnAdapter
import build.wallet.sqldelight.adapter.InstantColumnAdapter
import build.wallet.sqldelight.adapter.WireColumnAdapter
import build.wallet.sqldelight.withLogging

class BitkeyDatabaseProviderImpl(sqlDriverFactory: SqlDriverFactory) : BitkeyDatabaseProvider {
  private val sqlDriver by lazy {
    sqlDriverFactory
      .createDriver(
        dataBaseName = "bitkey.db",
        dataBaseSchema = BitkeyDatabase.Schema
      )
      .withLogging(tag = "BitkeyDatabase")
  }

  private val debugSqlDriver by lazy {
    sqlDriverFactory
      .createDriver(
        dataBaseName = "bitkeyDebug.db",
        dataBaseSchema = BitkeyDebugDatabase.Schema
      )
      .withLogging(tag = "BitkeyDebugDb")
  }

  private val database by lazy {
    BitkeyDatabase(
      driver = sqlDriver,
      liteAccountEntityAdapter =
        LiteAccountEntity.Adapter(
          accountIdAdapter = LiteAccountIdColumnAdapter,
          appRecoveryAuthKeyAdapter = AppRecoveryAuthPublicKeyColumnAdapter,
          bitcoinNetworkTypeAdapter = EnumColumnAdapter(),
          f8eEnvironmentAdapter = F8eEnvironmentColumnAdapter
        ),
      activeLiteAccountEntityAdapter =
        ActiveLiteAccountEntity.Adapter(
          accountIdAdapter = LiteAccountIdColumnAdapter
        ),
      onboardingLiteAccountEntityAdapter =
        OnboardingLiteAccountEntity.Adapter(
          accountIdAdapter = LiteAccountIdColumnAdapter
        ),
      fullAccountEntityAdapter =
        FullAccountEntity.Adapter(
          accountIdAdapter = FullAccountIdColumnAdapter
        ),
      activeFullAccountEntityAdapter =
        ActiveFullAccountEntity.Adapter(
          accountIdAdapter = FullAccountIdColumnAdapter
        ),
      onboardingFullAccountEntityAdapter =
        OnboardingFullAccountEntity.Adapter(
          accountIdAdapter = FullAccountIdColumnAdapter
        ),
      keyboxEntityAdapter =
        KeyboxEntity.Adapter(
          accountAdapter = FullAccountColumnAdapter,
          inactiveKeysetIdsAdapter = InactiveKeysetIdsColumnAdapter,
          networkTypeAdapter = EnumColumnAdapter(),
          f8eEnvironmentAdapter = F8eEnvironmentColumnAdapter,
          delayNotifyDurationAdapter = DurationColumnAdapter,
          appGlobalAuthKeyHwSignatureAdapter = AppGlobalAuthKeyHwSignatureColumnAdapter
        ),
      transactionDetailEntityAdapter =
        TransactionDetailEntity.Adapter(
          broadcastTimeInstantAdapter = InstantColumnAdapter,
          estimatedConfirmationTimeInstantAdapter = InstantColumnAdapter
        ),
      eventEntityAdapter =
        EventEntity.Adapter(
          eventAdapter = WireColumnAdapter(Event.ADAPTER),
          f8eEnvironmentAdapter = F8eEnvironmentColumnAdapter
        ),
      gettingStartedTaskEntityAdapter =
        GettingStartedTaskEntity.Adapter(
          taskIdAdapter = EnumColumnAdapter(),
          taskStateAdapter = EnumColumnAdapter()
        ),
      exchangeRateEntityAdapter =
        ExchangeRateEntity.Adapter(
          fromCurrencyAdapter = IsoCurrencyTextCodeColumnAdapter,
          toCurrencyAdapter = IsoCurrencyTextCodeColumnAdapter,
          timeRetrievedAdapter = InstantColumnAdapter
        ),
      historicalExchangeRateEntityAdapter =
        HistoricalExchangeRateEntity.Adapter(
          timeAdapter = InstantColumnAdapter,
          fromCurrencyAdapter = IsoCurrencyTextCodeColumnAdapter,
          toCurrencyAdapter = IsoCurrencyTextCodeColumnAdapter
        ),
      onboardingStepStateEntityAdapter =
        OnboardingStepStateEntity.Adapter(
          stepIdAdapter = EnumColumnAdapter(),
          stateAdapter = EnumColumnAdapter()
        ),
      appKeyBundleEntityAdapter =
        AppKeyBundleEntity.Adapter(
          globalAuthKeyAdapter = AppGlobalAuthPublicKeyColumnAdapter,
          recoveryAuthKeyAdapter = AppRecoveryAuthPublicKeyColumnAdapter,
          spendingKeyAdapter = AppSpendingPublicKeyColumnAdapter
        ),
      hwKeyBundleEntityAdapter =
        HwKeyBundleEntity.Adapter(
          spendingKeyAdapter = HwSpendingPublicKeyColumnAdapter,
          authKeyAdapter = HwAuthPublicKeyColumnAdapter
        ),
      registerWatchAddressEntityAdapter =
        RegisterWatchAddressEntity.Adapter(
          addressAdapter = BitcoinAddressColumnAdapter,
          f8eEnvironmentAdapter = F8eEnvironmentColumnAdapter
        ),
      spendingKeysetEntityAdapter =
        SpendingKeysetEntity.Adapter(
          appKeyAdapter = AppSpendingPublicKeyColumnAdapter,
          hardwareKeyAdapter = HwSpendingPublicKeyColumnAdapter,
          serverKeyAdapter = F8eSpendingPublicKeyColumnAdapter
        ),
      activeServerRecoveryEntityAdapter =
        ActiveServerRecoveryEntity.Adapter(
          accountAdapter = FullAccountColumnAdapter,
          startTimeAdapter = InstantColumnAdapter,
          endTimeAdapter = InstantColumnAdapter,
          lostFactorAdapter = EnumColumnAdapter(),
          destinationAppGlobalAuthKeyAdapter = AppGlobalAuthPublicKeyColumnAdapter,
          destinationAppRecoveryAuthKeyAdapter = AppRecoveryAuthPublicKeyColumnAdapter,
          destinationHardwareAuthKeyAdapter = HwAuthPublicKeyColumnAdapter
        ),
      localRecoveryAttemptEntityAdapter =
        LocalRecoveryAttemptEntity.Adapter(
          accountAdapter = FullAccountColumnAdapter,
          destinationAppGlobalAuthKeyAdapter = AppGlobalAuthPublicKeyColumnAdapter,
          destinationAppRecoveryAuthKeyAdapter = AppRecoveryAuthPublicKeyColumnAdapter,
          destinationHardwareAuthKeyAdapter = HwAuthPublicKeyColumnAdapter,
          destinationAppSpendingKeyAdapter = AppSpendingPublicKeyColumnAdapter,
          destinationHardwareSpendingKeyAdapter = HwSpendingPublicKeyColumnAdapter,
          appGlobalAuthKeyHwSignatureAdapter = AppGlobalAuthKeyHwSignatureColumnAdapter,
          serverSpendingKeyAdapter = F8eSpendingPublicKeyColumnAdapter,
          lostFactorAdapter = EnumColumnAdapter(),
          sealedCsekAdapter = ByteStringColumnAdapter
        ),
      emailTouchpointEntityAdapter =
        EmailTouchpointEntity.Adapter(
          emailAdapter = EmailColumnAdapter
        ),
      spendingLimitEntityAdapter =
        SpendingLimitEntity.Adapter(
          limitAmountCurrencyAlphaCodeAdapter = IsoCurrencyTextCodeColumnAdapter,
          limitTimeZoneZoneIdAdapter = TimeZoneColumnAdapter
        ),
      fiatCurrencyPreferenceEntityAdapter =
        FiatCurrencyPreferenceEntity.Adapter(
          currencyAdapter = IsoCurrencyTextCodeColumnAdapter
        ),
      firmwareDeviceInfoEntityAdapter =
        FirmwareDeviceInfoEntity.Adapter(
          activeSlotAdapter = EnumColumnAdapter(),
          secureBootConfigAdapter = EnumColumnAdapter()
        ),
      fwupDataEntityAdapter =
        FwupDataEntity.Adapter(
          fwupModeAdapter = EnumColumnAdapter()
        ),
      templateFullAccountConfigEntityAdapter =
        TemplateFullAccountConfigEntity.Adapter(
          bitcoinNetworkTypeAdapter = EnumColumnAdapter(),
          f8eEnvironmentAdapter = F8eEnvironmentColumnAdapter,
          delayNotifyDurationAdapter = DurationColumnAdapter
        ),
      priorityPreferenceEntityAdapter =
        PriorityPreferenceEntity.Adapter(
          priorityAdapter = EnumColumnAdapter()
        ),
      homeUiBottomSheetEntityAdapter =
        HomeUiBottomSheetEntity.Adapter(
          sheetIdAdapter = EnumColumnAdapter()
        ),
      bitcoinDisplayPreferenceEntityAdapter =
        BitcoinDisplayPreferenceEntity.Adapter(
          displayUnitAdapter = EnumColumnAdapter()
        ),
      fiatCurrencyEntityAdapter =
        FiatCurrencyEntity.Adapter(
          textCodeAdapter = IsoCurrencyTextCodeColumnAdapter
        ),
      fiatCurrencyMobilePayConfigurationEntityAdapter =
        FiatCurrencyMobilePayConfigurationEntity.Adapter(
          textCodeAdapter = IsoCurrencyTextCodeColumnAdapter,
          snapValuesAdapter = MobilePaySnapValueColumnAdapter
        ),
      socRecProtectedCustomerEntityAdapter =
        SocRecProtectedCustomerEntity.Adapter(
          aliasAdapter = ProtectedCustomerAliasColumnAdapter
        ),
      socRecTrustedContactInvitationEntityAdapter =
        SocRecTrustedContactInvitationEntity.Adapter(
          trustedContactAliasAdapter = TrustedContactAliasColumnAdapter,
          expiresAtAdapter = InstantColumnAdapter
        ),
      socRecTrustedContactEntityAdapter =
        SocRecTrustedContactEntity.Adapter(
          trustedContactAliasAdapter = TrustedContactAliasColumnAdapter,
          authenticationStateAdapter = EnumColumnAdapter()
        ),
      socRecEnrollmentAuthenticationAdapter =
        SocRecEnrollmentAuthentication.Adapter(
          protectedCustomerEnrollmentPakeKeyAdapter =
            SocRecKeyColumnAdapter(
              ::ProtectedCustomerEnrollmentPakeKey,
              CurveType.Curve25519
            ),
          pakeCodeAdapter = ByteStringColumnAdapter
        ),
      socRecStartedChallengeAuthenticationAdapter =
        SocRecStartedChallengeAuthentication.Adapter(
          protectedCustomerRecoveryPakeKeyAdapter =
            SocRecKeyColumnAdapter(
              ::ProtectedCustomerRecoveryPakeKey,
              CurveType.Curve25519
            ),
          pakeCodeAdapter = ByteStringColumnAdapter
        ),
      socRecUnendorsedTrustedContactEntityAdapter =
        SocRecUnendorsedTrustedContactEntity.Adapter(
          trustedContactAliasAdapter = TrustedContactAliasColumnAdapter,
          enrollmentPakeKeyAdapter =
            SocRecKeyColumnAdapter(
              ::TrustedContactEnrollmentPakeKey,
              CurveType.Curve25519
            ),
          enrollmentKeyConfirmationAdapter = ByteStringColumnAdapter,
          authenticationStateAdapter = EnumColumnAdapter()
        ),
      socRecKeysAdapter =
        SocRecKeys.Adapter(
          purposeAdapter = EnumColumnAdapter(),
          keyAdapter = PublicKeyColumnAdapter
        ),
      networkReachabilityEventEntityAdapter =
        NetworkReachabilityEventEntity.Adapter(
          connectionAdapter = NetworkConnectionColumnAdapter,
          reachabilityAdapter = EnumColumnAdapter(),
          timestampAdapter = InstantColumnAdapter
        ),
      onboardingKeyboxHwAuthPublicKeyAdapter =
        OnboardingKeyboxHwAuthPublicKey.Adapter(
          hwAuthPublicKeyAdapter = HwAuthPublicKeyColumnAdapter,
          appGlobalAuthKeyHwSignatureAdapter = AppGlobalAuthKeyHwSignatureColumnAdapter
        ),
      authKeyRotationAttemptEntityAdapter =
        AuthKeyRotationAttemptEntity.Adapter(
          destinationAppGlobalAuthKeyAdapter = AppGlobalAuthPublicKeyColumnAdapter,
          destinationAppRecoveryAuthKeyAdapter = AppRecoveryAuthPublicKeyColumnAdapter,
          destinationAppGlobalAuthKeyHwSignatureAdapter = AppGlobalAuthKeyHwSignatureColumnAdapter
        )
    )
  }

  private val debugDatabase by lazy {
    BitkeyDebugDatabase(
      driver = debugSqlDriver,
      onboardingStepSkipConfigEntityAdapter =
        OnboardingStepSkipConfigEntity.Adapter(
          onboardingStepAdapter = EnumColumnAdapter()
        )
    )
  }

  override fun database(): BitkeyDatabase = database

  override fun debugDatabase(): BitkeyDebugDatabase = debugDatabase
}
