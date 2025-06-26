package build.wallet.database

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import build.wallet.analytics.v1.Event
import build.wallet.bitkey.inheritance.InheritanceMaterialHash
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.database.adapters.*
import build.wallet.database.adapters.bitkey.*
import build.wallet.database.sqldelight.*
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.sqldelight.SqlDriverFactory
import build.wallet.sqldelight.adapter.ByteStringColumnAdapter
import build.wallet.sqldelight.adapter.InstantAsEpochMillisecondsColumnAdapter
import build.wallet.sqldelight.adapter.InstantAsIso8601ColumnAdapter
import build.wallet.sqldelight.adapter.WireColumnAdapter
import build.wallet.sqldelight.withLogging
import kotlinx.coroutines.*

@BitkeyInject(AppScope::class)
class BitkeyDatabaseProviderImpl(
  sqlDriverFactory: SqlDriverFactory,
  appScope: CoroutineScope,
) : BitkeyDatabaseProvider {
  constructor(sqlDriverFactory: SqlDriverFactory) :
    this(sqlDriverFactory, CoroutineScope(SupervisorJob()))

  private val database: Deferred<BitkeyDatabase> =
    appScope.async(Dispatchers.IO, CoroutineStart.LAZY) {
      val driver = sqlDriverFactory
        .createDriver(
          dataBaseName = "bitkey.db",
          dataBaseSchema = BitkeyDatabase.Schema
        )
        .withLogging(tag = "BitkeyDatabase")
      createDatabase(driver)
    }

  private val debugDatabase: Deferred<BitkeyDebugDatabase> =
    appScope.async(Dispatchers.IO, CoroutineStart.LAZY) {
      val driver = sqlDriverFactory
        .createDriver(
          dataBaseName = "bitkeyDebug.db",
          dataBaseSchema = BitkeyDebugDatabase.Schema
        )
        .withLogging(tag = "BitkeyDebugDb")
      BitkeyDebugDatabase(driver = driver)
    }

  override suspend fun database(): BitkeyDatabase {
    return database.await()
  }

  override suspend fun debugDatabase(): BitkeyDebugDatabase {
    return debugDatabase.await()
  }

  private fun createDatabase(driver: SqlDriver): BitkeyDatabase {
    return BitkeyDatabase(
      driver = driver,
      liteAccountEntityAdapter =
        LiteAccountEntity.Adapter(
          accountIdAdapter = LiteAccountIdColumnAdapter,
          appRecoveryAuthKeyAdapter = PublicKeyColumnAdapter(),
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
      softwareAccountEntityAdapter =
        SoftwareAccountEntity.Adapter(
          accountIdAdapter = SoftwareAccountIdColumnAdapter,
          bitcoinNetworkTypeAdapter = EnumColumnAdapter(),
          f8eEnvironmentAdapter = F8eEnvironmentColumnAdapter
        ),
      activeSoftwareAccountEntityAdapter =
        ActiveSoftwareAccountEntity.Adapter(
          accountIdAdapter = SoftwareAccountIdColumnAdapter
        ),
      onboardingSoftwareAccountEntityAdapter =
        OnboardingSoftwareAccountEntity.Adapter(
          accountIdAdapter = SoftwareAccountIdColumnAdapter,
          appGlobalAuthKeyAdapter = PublicKeyColumnAdapter(),
          appRecoveryAuthKeyAdapter = PublicKeyColumnAdapter()
        ),
      keyboxEntityAdapter =
        KeyboxEntity.Adapter(
          accountAdapter = FullAccountColumnAdapter,
          networkTypeAdapter = EnumColumnAdapter(),
          f8eEnvironmentAdapter = F8eEnvironmentColumnAdapter,
          delayNotifyDurationAdapter = DurationColumnAdapter,
          appGlobalAuthKeyHwSignatureAdapter = AppGlobalAuthKeyHwSignatureColumnAdapter
        ),
      transactionDetailEntityAdapter = TransactionDetailEntity.Adapter(
        broadcastTimeAdapter = InstantAsIso8601ColumnAdapter,
        estimatedConfirmationTimeAdapter = InstantAsIso8601ColumnAdapter
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
          timeRetrievedAdapter = InstantAsEpochMillisecondsColumnAdapter
        ),
      historicalExchangeRateEntityAdapter =
        HistoricalExchangeRateEntity.Adapter(
          timeAdapter = InstantAsEpochMillisecondsColumnAdapter,
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
          globalAuthKeyAdapter = PublicKeyColumnAdapter(),
          recoveryAuthKeyAdapter = PublicKeyColumnAdapter(),
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
          startTimeAdapter = InstantAsEpochMillisecondsColumnAdapter,
          endTimeAdapter = InstantAsEpochMillisecondsColumnAdapter,
          lostFactorAdapter = EnumColumnAdapter(),
          destinationAppGlobalAuthKeyAdapter = PublicKeyColumnAdapter(),
          destinationAppRecoveryAuthKeyAdapter = PublicKeyColumnAdapter(),
          destinationHardwareAuthKeyAdapter = HwAuthPublicKeyColumnAdapter
        ),
      localRecoveryAttemptEntityAdapter =
        LocalRecoveryAttemptEntity.Adapter(
          accountAdapter = FullAccountColumnAdapter,
          destinationAppGlobalAuthKeyAdapter = PublicKeyColumnAdapter(),
          destinationAppRecoveryAuthKeyAdapter = PublicKeyColumnAdapter(),
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
      defaultAccountConfigEntityAdapter = DefaultAccountConfigEntity.Adapter(
        bitcoinNetworkTypeAdapter = EnumColumnAdapter(),
        f8eEnvironmentAdapter = F8eEnvironmentColumnAdapter,
        delayNotifyDurationAdapter = DurationColumnAdapter
      ),
      priorityPreferenceEntityAdapter =
        PriorityPreferenceEntity.Adapter(
          priorityAdapter = EnumColumnAdapter()
        ),
      bitcoinDisplayPreferenceEntityAdapter =
        BitcoinDisplayPreferenceEntity.Adapter(
          displayUnitAdapter = EnumColumnAdapter()
        ),
      fiatCurrencyEntityAdapter =
        FiatCurrencyEntity.Adapter(
          textCodeAdapter = IsoCurrencyTextCodeColumnAdapter
        ),
      protectedCustomerEntityAdapter =
        ProtectedCustomerEntity.Adapter(
          aliasAdapter = ProtectedCustomerAliasColumnAdapter,
          rolesAdapter = StringSetAdapter(
            DelegatedColumnAdapter(
              ::TrustedContactRole,
              TrustedContactRole::key
            )
          )
        ),
      trustedContactInvitationEntityAdapter =
        TrustedContactInvitationEntity.Adapter(
          trustedContactAliasAdapter = TrustedContactAliasColumnAdapter,
          expiresAtAdapter = InstantAsEpochMillisecondsColumnAdapter,
          rolesAdapter = StringSetAdapter(
            DelegatedColumnAdapter(
              ::TrustedContactRole,
              TrustedContactRole::key
            )
          )
        ),
      trustedContactEntityAdapter =
        TrustedContactEntity.Adapter(
          trustedContactAliasAdapter = TrustedContactAliasColumnAdapter,
          authenticationStateAdapter = EnumColumnAdapter(),
          rolesAdapter = StringSetAdapter(
            DelegatedColumnAdapter(
              ::TrustedContactRole,
              TrustedContactRole::key
            )
          )
        ),
      socRecEnrollmentAuthenticationAdapter =
        SocRecEnrollmentAuthentication.Adapter(
          protectedCustomerEnrollmentPakeKeyAdapter =
            PublicKeyColumnAdapter(),
          pakeCodeAdapter = ByteStringColumnAdapter
        ),
      socRecStartedChallengeAuthenticationAdapter =
        SocRecStartedChallengeAuthentication.Adapter(
          protectedCustomerRecoveryPakeKeyAdapter =
            PublicKeyColumnAdapter(),
          pakeCodeAdapter = ByteStringColumnAdapter
        ),
      unendorsedTrustedContactEntityAdapter =
        UnendorsedTrustedContactEntity.Adapter(
          trustedContactAliasAdapter = TrustedContactAliasColumnAdapter,
          enrollmentPakeKeyAdapter = PublicKeyColumnAdapter(),
          enrollmentKeyConfirmationAdapter = ByteStringColumnAdapter,
          authenticationStateAdapter = EnumColumnAdapter(),
          rolesAdapter = StringSetAdapter(
            DelegatedColumnAdapter(
              ::TrustedContactRole,
              TrustedContactRole::key
            )
          )
        ),
      socRecKeysAdapter =
        SocRecKeys.Adapter(
          purposeAdapter = EnumColumnAdapter(),
          keyAdapter = PublicKeyColumnAdapter()
        ),
      networkReachabilityEventEntityAdapter =
        NetworkReachabilityEventEntity.Adapter(
          connectionAdapter = NetworkConnectionColumnAdapter,
          reachabilityAdapter = EnumColumnAdapter(),
          timestampAdapter = InstantAsEpochMillisecondsColumnAdapter
        ),
      onboardingKeyboxHwAuthPublicKeyAdapter =
        OnboardingKeyboxHwAuthPublicKey.Adapter(
          hwAuthPublicKeyAdapter = HwAuthPublicKeyColumnAdapter,
          appGlobalAuthKeyHwSignatureAdapter = AppGlobalAuthKeyHwSignatureColumnAdapter
        ),
      authKeyRotationAttemptEntityAdapter =
        AuthKeyRotationAttemptEntity.Adapter(
          destinationAppGlobalAuthKeyAdapter = PublicKeyColumnAdapter(),
          destinationAppRecoveryAuthKeyAdapter = PublicKeyColumnAdapter(),
          destinationAppGlobalAuthKeyHwSignatureAdapter = AppGlobalAuthKeyHwSignatureColumnAdapter
        ),
      partnershipTransactionEntityAdapter = PartnershipTransactionEntity.Adapter(
        partnerIdAdapter = DelegatedColumnAdapter(::PartnerId, PartnerId::value),
        transactionIdAdapter = DelegatedColumnAdapter(
          ::PartnershipTransactionId,
          PartnershipTransactionId::value
        ),
        fiatCurrencyAdapter = DelegatedColumnAdapter(
          ::IsoCurrencyTextCode,
          IsoCurrencyTextCode::code
        ),
        typeAdapter = EnumColumnAdapter(),
        statusAdapter = EnumColumnAdapter(),
        createdAdapter = InstantAsIso8601ColumnAdapter,
        updatedAdapter = InstantAsIso8601ColumnAdapter
      ),
      coachmarkEntityAdapter = CoachmarkEntity.Adapter(
        idAdapter = EnumColumnAdapter(),
        expirationAdapter = InstantAsEpochMillisecondsColumnAdapter
      ),
      softwareKeyboxEntityAdapter = SoftwareKeyboxEntity.Adapter(
        accountIdAdapter = SoftwareAccountIdColumnAdapter,
        appGlobalAuthKeyAdapter = PublicKeyColumnAdapter(),
        appRecoveryAuthKeyAdapter = PublicKeyColumnAdapter()
      ),
      securityRecommendationInteractionEntityAdapter =
        SecurityRecommendationInteractionEntity.Adapter(
          interactionStatusAdapter = EnumColumnAdapter(),
          lastInteractedAtAdapter = InstantAsEpochMillisecondsColumnAdapter,
          lastRecommendationTriggeredAtAdapter = InstantAsEpochMillisecondsColumnAdapter,
          recordUpdatedAtAdapter = InstantAsEpochMillisecondsColumnAdapter
        ),
      inheritanceDataEntityAdapter = InheritanceDataEntity.Adapter(
        lastSyncHashAdapter = DelegatedColumnAdapter(
          ::InheritanceMaterialHash,
          InheritanceMaterialHash::value
        )
          .then(DelegatedColumnAdapter(Long::toInt, Int::toLong)),
        lastSyncTimestampAdapter = InstantAsEpochMillisecondsColumnAdapter
      ),
      pendingBenefactorClaimEntityAdapter = PendingBenefactorClaimEntity.Adapter(
        claimIdAdapter = InheritanceClaimIdColumnAdapter,
        relationshipIdAdapter = RelationshipIdColumnAdapter,
        delayEndTimeAdapter = InstantAsEpochMillisecondsColumnAdapter,
        delayStartTimeAdapter = InstantAsEpochMillisecondsColumnAdapter
      ),
      pendingBeneficiaryClaimEntityAdapter = PendingBeneficiaryClaimEntity.Adapter(
        claimIdAdapter = InheritanceClaimIdColumnAdapter,
        relationshipIdAdapter = RelationshipIdColumnAdapter,
        delayEndTimeAdapter = InstantAsEpochMillisecondsColumnAdapter,
        delayStartTimeAdapter = InstantAsEpochMillisecondsColumnAdapter
      ),
      mobileMetricEntityAdapter = MobileMetricEntity.Adapter(
        lastUpdatedAdapter = InstantAsIso8601ColumnAdapter
      ),
      walletBalanceEntityAdapter = WalletBalanceEntity.Adapter(
        dateAdapter = InstantAsIso8601ColumnAdapter,
        fiatCurrencyCodeAdapter = DelegatedColumnAdapter(
          ::IsoCurrencyTextCode,
          IsoCurrencyTextCode::code
        ),
        rangeAdapter = EnumColumnAdapter()
      ),
      hardwareUnlockMethodsAdapter = HardwareUnlockMethods.Adapter(
        unlockMethodAdapter = EnumColumnAdapter(),
        createdAtAdapter = InstantAsIso8601ColumnAdapter
      ),
      chartRangePreferenceEntityAdapter = ChartRangePreferenceEntity.Adapter(
        timeScaleAdapter = EnumColumnAdapter()
      ),
      txVerificationPolicyEntityAdapter = TxVerificationPolicyEntity.Adapter(
        thresholdCurrencyAlphaCodeAdapter = IsoCurrencyTextCodeColumnAdapter,
        delayEndTimeAdapter = InstantAsIso8601ColumnAdapter
      )
    )
  }
}
