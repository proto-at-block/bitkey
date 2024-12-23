package build.wallet.di

import build.wallet.bdk.BdkBlockchainFactoryImpl
import build.wallet.bdk.bindings.BdkBlockchainFactory
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.cloud.store.*
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.SignatureVerifier
import build.wallet.firmware.*
import build.wallet.money.exchange.ExchangeRateF8eClient
import build.wallet.money.exchange.ExchangeRateF8eClientFake
import build.wallet.money.exchange.ExchangeRateF8eClientImpl
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.platform.NfcSessionProvider
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.sharing.SharingManagerFake
import build.wallet.recovery.RecoverySyncFrequency
import build.wallet.recovery.RecoverySyncFrequencyComponent
import build.wallet.relationships.RelationshipsCryptoFake
import build.wallet.time.ControlledDelayer
import build.wallet.time.Delayer
import build.wallet.time.DelayerComponent
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.*
import kotlin.time.Duration.Companion.seconds

/**
 * Component bound to [AppScope] to be used by the JVM test code.
 * Tied to [AppTester] instance.
 */
@Suppress("TooManyFunctions")
@MergeComponent(
  scope = AppScope::class,
  exclude = [
    // Components and interfaces that we replace for testing purposes.
    // These interfaces are substituted below using `@Provides`.
    BdkBlockchainFactoryImpl::class,
    CloudKeyValueStoreImpl::class,
    DelayerComponent::class,
    ExchangeRateF8eClientImpl::class,
    RecoverySyncFrequencyComponent::class
  ]
)
@SingleIn(AppScope::class)
abstract class JvmAppComponentImpl(
  @get:Provides val appDir: String?,
  @get:Provides val bdkBlockchainFactory: BdkBlockchainFactory,
  @get:Provides override val writableCloudStoreAccountRepository:
    WritableCloudStoreAccountRepository,
  @get:Provides override val cloudKeyValueStore: CloudKeyValueStore,
  @get:Provides override val cloudFileStore: CloudFileStore,
) : JvmAppComponent, JvmActivityComponent.Factory {
  @Provides
  @SingleIn(AppScope::class) // RelationshipsCryptoFake has state.
  fun provideRelationshipsCryptoFake(
    messageSigner: MessageSigner,
    signatureVerifier: SignatureVerifier,
    appPrivateKeyDao: AppPrivateKeyDao,
  ) = RelationshipsCryptoFake(
    messageSigner = messageSigner,
    signatureVerifier = signatureVerifier,
    appPrivateKeyDao = appPrivateKeyDao
  )

  @Provides
  fun provideCloudStoreAccountRepository(
    writableCloudStoreAccountRepository: WritableCloudStoreAccountRepository,
  ): CloudStoreAccountRepository = writableCloudStoreAccountRepository

  /** Use shorter recovery sync frequency for testing purposes. */
  @Provides
  fun recoverySyncFrequency() = RecoverySyncFrequency(5.seconds)

  @Provides
  @SingleIn(AppScope::class) // SharingManagerFake is stateful.
  fun provideSharingManagerFake(): SharingManagerFake = SharingManagerFake()

  @Provides
  fun provideSharingManager(fake: SharingManagerFake): SharingManager = fake

  @Provides
  fun provideHardwareAttestation(): HardwareAttestation = HardwareAttestationFake()

  @Provides
  fun provideDelayer(): Delayer = ControlledDelayer()

  // we pass a fake exchange rate service to avoid calls to 3rd party exchange rate services during tests
  @Provides
  @SingleIn(AppScope::class) // ExchangeRateF8eClientFake is stateful.
  fun provideExchangeRateF8eClient(): ExchangeRateF8eClient = ExchangeRateF8eClientFake()

  @Provides
  fun provideTeltra(): Teltra = TeltraFake()

  @Provides
  fun provideFirmwareCommsLogBuffer(): FirmwareCommsLogBuffer = FirmwareCommsLogBufferFake()

  @Provides
  fun provideNfcSessionProvider(): NfcSessionProvider = NfcSessionFake.Companion
}
