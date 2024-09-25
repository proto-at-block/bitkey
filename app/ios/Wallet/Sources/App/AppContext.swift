import Foundation
import Shared

// MARK: -

/**
 * A set of dependencies that persist across all states of the app.
 */
class AppContext {

    // MARK: - Classes

    let appUiStateMachineManager: AppUiStateMachineManager
    let biometricPromptUiStateMachineManager: BiometricPromptUiStateMachineManager

    let notificationManager: NotificationManager

    let appComponent: AppComponentImpl
    let activityComponent: ActivityComponent

    let bdkAddressBuilder: BdkAddressBuilder
    let bdkBlockchainFactory: BdkBlockchainFactory
    let bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactory
    let bdkMnemonicGenerator: BdkMnemonicGenerator
    let bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGenerator
    let bdkDescriptorFactory: BdkDescriptorFactory
    let bdkDescriptorSecretKeyFactory: BdkDescriptorSecretKeyFactory
    let bdkWalletFactory: BdkWalletFactory
    let bdkPsbtBuilder: BdkPartiallySignedTransactionBuilder
    let bdkTxBuilderFactory: BdkTxBuilderFactory

    let deviceTokenProvider: DeviceTokenProvider
    let sharingManager: SharingManagerImpl
    let datadogTracer: DatadogTracer
    let secp256k1KeyGenerator: Secp256k1KeyGenerator
    let biometricsPrompter: BiometricPrompter

    // MARK: - Life Cycle

    init(appVariant: AppVariant) {
        self.deviceTokenProvider = DeviceTokenProviderImpl()

        self.bdkMnemonicGenerator = BdkMnemonicGeneratorImpl()
        self.bdkAddressBuilder = BdkAddressBuilderImpl()
        self.bdkBlockchainFactory = BdkBlockchainFactoryImpl()
        self.bdkBumpFeeTxBuilderFactory = BdkBumpFeeTxBuilderFactoryImpl()
        self.bdkDescriptorFactory = BdkDescriptorFactoryImpl()
        self.bdkDescriptorSecretKeyFactory = BdkDescriptorSecretKeyFactoryImpl()
        self.bdkDescriptorSecretKeyGenerator = BdkDescriptorSecretKeyGeneratorImpl()
        self.bdkWalletFactory = BdkWalletFactoryImpl()
        self.bdkTxBuilderFactory = BdkTxBuilderFactoryImpl()
        self.bdkPsbtBuilder = BdkPartiallySignedTransactionBuilderImpl()
        self.datadogTracer = DatadogTracerImpl()
        self.secp256k1KeyGenerator = Secp256k1KeyGeneratorImpl()
        self.biometricsPrompter = BiometricPrompterImpl()

        let iCloudAccountRepository = iCloudAccountRepositoryImpl()
        let cloudStoreAccountRepository = CloudStoreAccountRepositoryImpl(
            iCloudAccountRepository: iCloudAccountRepository
        )
        let datadogRumMonitor = DatadogRumMonitorImpl()

        self.appComponent = AppComponentImplKt.makeAppComponent(
            appVariant: appVariant,
            bdkAddressBuilder: bdkAddressBuilder,
            bdkBlockchainFactory: bdkBlockchainFactory,
            bdkBumpFeeTxBuilderFactory: bdkBumpFeeTxBuilderFactory,
            bdkDescriptorSecretKeyGenerator: bdkDescriptorSecretKeyGenerator,
            bdkMnemonicGenerator: bdkMnemonicGenerator,
            bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilderImpl(),
            bdkTxBuilderFactory: bdkTxBuilderFactory,
            bdkWalletFactory: bdkWalletFactory,
            biometricPrompter: biometricsPrompter,
            datadogRumMonitor: datadogRumMonitor,
            datadogTracer: DatadogTracerImpl(),
            deviceTokenConfigProvider: DeviceTokenConfigProviderImpl(
                deviceTokenProvider: deviceTokenProvider,
                appVariant: AppVariant.current()
            ),
            fileManagerProvider: { FileManagerImpl(fileDirectoryProvider: $0) },
            logWritersProvider: { [
                DatadogLogWriter(logWriterContextStore: $0, minSeverity: .info),
            ] },
            messageSigner: MessageSignerImpl(),
            phoneNumberLibBindings: PhoneNumberLibBindingsImpl(),
            signatureVerifier: SignatureVerifierImpl(),
            secp256k1KeyGenerator: secp256k1KeyGenerator,
            teltra: TeltraImpl(),
            hardwareAttestation: HardwareAttestationImpl(),
            deviceOs: DeviceOs.ios,
            wsmVerifier: WsmVerifierImpl(),
            cryptoBox: CryptoBoxImpl(),
            spake2: Spake2Impl(),
            symmetricKeyEncryptor: SymmetricKeyEncryptorImpl(),
            symmetricKeyGenerator: SymmetricKeyGeneratorImpl(),
            xChaCha20Poly1305: XChaCha20Poly1305Impl(),
            xNonceGenerator: XNonceGeneratorImpl(),
            firmwareCommsLogBuffer: FirmwareCommsLogBufferImpl()
        )

        self.notificationManager = NotificationManagerImpl(
            appVariant: appComponent.appVariant,
            deviceTokenManager: appComponent.deviceTokenManager,
            deviceTokenProvider: deviceTokenProvider,
            eventTracker: appComponent.eventTracker,
            pushNotificationPermissionStatusProvider: appComponent
                .pushNotificationPermissionStatusProvider
        )

        let fakeHardwareKeyStore = FakeHardwareKeyStoreImpl(
            bdkMnemonicGenerator: self.bdkMnemonicGenerator,
            bdkDescriptorSecretKeyGenerator: self.bdkDescriptorSecretKeyGenerator,
            secp256k1KeyGenerator: self.secp256k1KeyGenerator,
            encryptedKeyValueStoreFactory: appComponent.secureStoreFactory
        )

        let fakeHardwareSpendingWalletProvider = FakeHardwareSpendingWalletProvider(
            spendingWalletProvider: appComponent.spendingWalletProvider,
            descriptorBuilder: appComponent.bitcoinMultiSigDescriptorBuilder,
            fakeHardwareKeyStore: fakeHardwareKeyStore
        )

        let nfcCommandsProvider = NfcCommandsProvider(
            real: NfcCommandsImpl(),
            fake: NfcCommandsFake(
                messageSigner: appComponent.messageSigner,
                fakeHardwareKeyStore: fakeHardwareKeyStore,
                fakeHardwareSpendingWalletProvider: fakeHardwareSpendingWalletProvider
            )
        )

        self.sharingManager = SharingManagerImpl()
        let appViewController = HiddenBarNavigationController()

        self.activityComponent = ActivityComponentImpl(
            appComponent: appComponent,
            cloudKeyValueStore: CloudKeyValueStoreImpl(
                iCloudKeyValueStore: iCloudKeyValueStoreImpl(clock: appComponent.clock)
            ),
            cloudFileStore: CloudFileStoreImpl(iCloudDriveFileStore: iCloudDriveFileStore()),
            cloudSignInUiStateMachine: CloudSignInUiStateMachineImpl(
                cloudStoreAccountRepository: cloudStoreAccountRepository,
                delayer: appComponent.delayer
            ),
            cloudDevOptionsStateMachine: CloudDevOptionsStateMachineImpl(
                iCloudAccountRepository: iCloudAccountRepository
            ),
            cloudStoreAccountRepository: cloudStoreAccountRepository,
            datadogRumMonitor: DatadogRumMonitorImpl(),
            lightningInvoiceParser: LightningInvoiceParserImpl(),
            sharingManager: sharingManager,
            systemSettingsLauncher: SystemSettingsLauncherImpl(),
            inAppBrowserNavigator: InAppBrowserNavigatorImpl(appViewController: appViewController),
            nfcCommandsProvider: nfcCommandsProvider,
            nfcSessionProvider: NfcSessionProviderImpl(),
            pdfAnnotatorFactory: PdfAnnotatorFactoryImpl(),
            biometricPrompter: biometricsPrompter,
            fakeHardwareKeyStore: fakeHardwareKeyStore
        )

        self.appUiStateMachineManager = AppUiStateMachineManagerImpl(
            appUiStateMachine: activityComponent.appUiStateMachine as! AppUiStateMachineImpl,
            appViewController: appViewController,
            context: .init(
                qrCodeScannerViewControllerFactory: QRCodeScannerViewControllerFactoryImpl()
            )
        )

        self.biometricPromptUiStateMachineManager = BiometricPromptUiStateMachineManager(
            biometricPromptUiStateMachine: activityComponent
                .biometricPromptUiStateMachine as! BiometricPromptUiStateMachineImpl,
            appViewController: HiddenBarNavigationController()
        )
    }
}

class NfcSessionProviderImpl: NfcSessionProvider {
    func get(parameters: NfcSessionParameters) throws -> NfcSession {
        if parameters.isHardwareFake {
            return NfcSessionFake(parameters: parameters)
        } else {
            return try NfcSessionImpl(parameters: parameters)
        }
    }
}
