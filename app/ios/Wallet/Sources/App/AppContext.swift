import Foundation
import Shared
import UIKit

// MARK: -

/**
 * A set of dependencies that persist across all states of the app.
 */
class AppContext {

    // MARK: - Classes

    let notificationManager: NotificationManager

    let appComponent: IosAppComponent
    let activityComponent: IosActivityComponent

    let deviceTokenProvider: DeviceTokenProvider
    let sharingManager: SharingManagerImpl

    // MARK: - Life Cycle

    init(appVariant: AppVariant, window: UIWindow) {
        self.deviceTokenProvider = DeviceTokenProviderImpl()
        self.sharingManager = SharingManagerImpl()
        let datadogRumMonitor = DatadogRumMonitorImpl()
        let cloudFileStore = CloudFileStoreImpl(iCloudDriveFileStore: iCloudDriveFileStore())
        let datadogTracer = DatadogTracerImpl()
        let secp256k1KeyGenerator = Secp256k1KeyGeneratorImpl()
        let biometricsPrompter = BiometricPrompterImpl()
        let noiseInitiator = NoiseInitiatorImpl(
            secureEnclave: SecureEnclaveImpl(),
            symmetricKeyGenerator: SymmetricKeyGeneratorImpl()
        )

        // Create IosAppComponent with iOS implementations
        self.appComponent = IosAppComponentCreateComponentKt.create(
            appVariant: appVariant,
            bdkAddressBuilder: BdkAddressBuilderImpl(),
            bdkBlockchainFactory: BdkBlockchainFactoryImpl(),
            bdkBumpFeeTxBuilderFactory: BdkBumpFeeTxBuilderFactoryImpl(),
            bdkDescriptorSecretKeyGenerator: BdkDescriptorSecretKeyGeneratorImpl(),
            bdkMnemonicGenerator: BdkMnemonicGeneratorImpl(),
            bdkPartiallySignedTransactionBuilder: BdkPartiallySignedTransactionBuilderImpl(),
            signatureUtils: SignatureUtilsImpl(),
            bdkTxBuilderFactory: BdkTxBuilderFactoryImpl(),
            bdkWalletFactory: BdkWalletFactoryImpl(),
            biometricPrompter: biometricsPrompter,
            cloudFileStore: cloudFileStore,
            cryptoBox: CryptoBoxImpl(),
            datadogRumMonitor: datadogRumMonitor,
            datadogTracer: datadogTracer,
            deviceTokenConfigProvider: DeviceTokenConfigProviderImpl(
                deviceTokenProvider: deviceTokenProvider,
                appVariant: appVariant
            ),
            fileManagerProvider: { FileManagerImpl(fileDirectoryProvider: $0) },
            firmwareCommsLogBuffer: FirmwareCommsLogBufferImpl(),
            frostWalletDescriptorFactory: FrostWalletDescriptorFactoryImpl(),
            hardwareAttestation: HardwareAttestationImpl(),
            inAppBrowserNavigator: InAppBrowserNavigatorImpl(window: window),
            lightningInvoiceParser: LightningInvoiceParserImpl(),
            logWritersProvider: { context in [DatadogLogWriter(
                logWriterContextStore: context,
                minSeverity: .info
            )] },
            messageSigner: MessageSignerImpl(),
            nfcCommandsImpl: BitkeyW1Commands(),
            nfcSessionProvider: NfcSessionProviderImpl(),
            pdfAnnotatorFactory: PdfAnnotatorFactoryImpl(),
            phoneNumberLibBindings: PhoneNumberLibBindingsImpl(),
            psbtUtils: PsbtUtilsImpl(),
            secp256k1KeyGenerator: secp256k1KeyGenerator,
            shareGenerator: ShareGeneratorImpl(),
            sharingManager: sharingManager,
            signatureVerifier: SignatureVerifierImpl(),
            spake2: Spake2Impl(),
            symmetricKeyEncryptor: SymmetricKeyEncryptorImpl(),
            symmetricKeyGenerator: SymmetricKeyGeneratorImpl(),
            systemSettingsLauncher: SystemSettingsLauncherImpl(),
            teltra: TeltraImpl(),
            wsmVerifier: WsmVerifierImpl(),
            xChaCha20Poly1305: XChaCha20Poly1305Impl(),
            xNonceGenerator: XNonceGeneratorImpl(),
            noiseInitiator: noiseInitiator,
            p256Box: P256BoxImpl(),
            chaincodeDelegationServerDpubGenerator: ServerChaincodeGeneratorImpl(),
            publicKeyUtils: PublicKeyUtilsImpl()
        )

        // Create IosActivityComponent
        self.activityComponent = appComponent.activityComponent()

        self.notificationManager = NotificationManagerImpl(
            appVariant: appComponent.appVariant,
            deviceTokenManager: appComponent.deviceTokenManager,
            deviceTokenProvider: deviceTokenProvider,
            eventTracker: appComponent.eventTracker,
            pushNotificationPermissionStatusProvider: appComponent
                .pushNotificationPermissionStatusProvider
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
