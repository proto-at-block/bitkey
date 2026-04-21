import CoreNFC
import firmware
import Shared

// MARK: - monomorphised FFI result types

enum IOResult<T> {
    case data(response: [UInt8])
    case result(value: T)
}

protocol IOCommand {
    associatedtype FFIStateType
    associatedtype ResultType

    func next(response: [UInt8]) throws -> FFIStateType
    func next(_: [UInt8]) throws -> IOResult<ResultType>
}

extension Version: IOCommand {
    typealias FFIStateType = U16State
    typealias ResultType = UInt16
}

extension StartFingerprintEnrollment: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension GetFingerprintEnrollmentStatus: IOCommand {
    typealias FFIStateType = firmware.FingerprintEnrollmentResultState
    typealias ResultType = firmware.FingerprintEnrollmentResult
}

extension DeleteFingerprint: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension GetUnlockMethod: IOCommand {
    typealias FFIStateType = firmware.UnlockInfoState
    typealias ResultType = firmware.UnlockInfo
}

extension GetEnrolledFingerprints: IOCommand {
    typealias FFIStateType = firmware.EnrolledFingerprintsState
    typealias ResultType = firmware.EnrolledFingerprints
}

extension SetFingerprintLabel: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension CancelFingerprintEnrollment: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension FingerprintResetRequest: IOCommand {
    typealias FFIStateType = BytesState
    typealias ResultType = [UInt8]
}

extension FingerprintResetFinalize: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension SignTransaction: IOCommand {
    typealias FFIStateType = PartiallySignedTransactionState
    typealias ResultType = String
}

extension WipeState: IOCommand {
    typealias FFIStateType = WipeStateResultState
    typealias ResultType = WipeStateResult
}

extension LockDevice: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension QueryAuthentication: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension ShowConfirmationScreen: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

// `SealKey` returns something it calls the `sealant` in bytes. The `sealant` should be the
// input to `UnsealKey` when you wish to unseal the key passed to `SealKey`.
extension SealKey: IOCommand {
    typealias FFIStateType = BytesState
    typealias ResultType = [UInt8]
}

// `UnsealKey` returns the unsealed key in bytes.
extension UnsealKey: IOCommand {
    typealias FFIStateType = BytesState
    typealias ResultType = [UInt8]
}

extension FwupStart: IOCommand {
    typealias FFIStateType = FwupStartResultState
    typealias ResultType = FwupStartResult
}

extension FwupTransfer: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension FwupFinish: IOCommand {
    typealias FFIStateType = FwupFinishRspStatusState
    typealias ResultType = FwupFinishRspStatus
}

extension GetFirmwareMetadata: IOCommand {
    typealias FFIStateType = FirmwareMetadataState
    typealias ResultType = firmware.FirmwareMetadata
}

extension GetDeviceIdentifiers: IOCommand {
    typealias FFIStateType = DeviceIdentifiersState
    typealias ResultType = DeviceIdentifiers
}

extension GetEvents: IOCommand {
    typealias FFIStateType = EventFragmentState
    typealias ResultType = firmware.EventFragment
}

extension GetTelemetryIdentifiers: IOCommand {
    typealias FFIStateType = TelemetryIdentifiersState
    typealias ResultType = firmware.TelemetryIdentifiers
}

extension GetFirmwareFeatureFlags: IOCommand {
    typealias FFIStateType = firmware.FirmwareFeatureFlagsState
    typealias ResultType = [firmware.FirmwareFeatureFlagCfg]
}

extension GetCoredumpCount: IOCommand {
    typealias FFIStateType = U16State
    typealias ResultType = UInt16
}

extension GetCoredumpFragment: IOCommand {
    typealias FFIStateType = CoredumpFragmentState
    typealias ResultType = firmware.CoredumpFragment
}

extension GetAuthenticationKey: IOCommand {
    typealias FFIStateType = PublicKeyState
    typealias ResultType = firmware.PublicKey
}

extension GetInitialSpendingKey: IOCommand {
    typealias FFIStateType = DescriptorPublicKeyState
    typealias ResultType = firmware.DescriptorPublicKey
}

extension GetNextSpendingKey: IOCommand {
    typealias FFIStateType = DescriptorPublicKeyState
    typealias ResultType = firmware.DescriptorPublicKey
}

extension SignChallenge: IOCommand {
    typealias FFIStateType = SignatureState
    typealias ResultType = Signature
}

extension GetDeviceInfo: IOCommand {
    typealias FFIStateType = DeviceInfoState
    typealias ResultType = firmware.DeviceInfo
}

extension GetCert: IOCommand {
    typealias FFIStateType = BytesState
    typealias ResultType = [UInt8]
}

extension SignVerifyAttestationChallenge: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension ProvisionAppAuthKey: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension GetConfirmationResult: IOCommand {
    typealias FFIStateType = ConfirmedCommandResultState
    typealias ResultType = ConfirmedCommandResult
}

extension SignStart: IOCommand {
    typealias FFIStateType = SignStartResultState
    typealias ResultType = SignStartResult
}

extension SignTransfer: IOCommand {
    typealias FFIStateType = SignTransferResultState
    typealias ResultType = SignTransferResult
}

extension GetAddress: IOCommand {
    typealias FFIStateType = GetAddressResultState
    typealias ResultType = GetAddressResult
}

extension VerifyKeysAndBuildDescriptor: IOCommand {
    typealias FFIStateType = SignatureState
    typealias ResultType = Signature
}

extension SignActionProof: IOCommand {
    typealias FFIStateType = SignActionProofResultState
    typealias ResultType = SignActionProofResult
}

extension LostAppRecovery: IOCommand {
    typealias FFIStateType = LostAppRecoveryResultState
    typealias ResultType = LostAppRecoveryResult
}

extension LostAppRecoveryContinue: IOCommand {
    typealias FFIStateType = LostAppRecoveryContinueResultState
    typealias ResultType = LostAppRecoveryContinueResult
}

extension LostAppRecoverySignChallenge: IOCommand {
    typealias FFIStateType = LostAppRecoverySignChallengeResultState
    typealias ResultType = LostAppRecoverySignChallengeResult
}

extension RotateAppAuthKeys: IOCommand {
    typealias FFIStateType = RotateAppAuthKeysResultState
    typealias ResultType = RotateAppAuthKeysResult
}

extension UpgradeRotateAppAuthKeys: IOCommand {
    typealias FFIStateType = UpgradeRotateAppAuthKeysResultState
    typealias ResultType = firmware.UpgradeRotateAppAuthKeysResult
}

extension firmware.SignChallengeAndSealSeks: IOCommand {
    typealias FFIStateType = SignChallengeAndSealSeksResultState
    typealias ResultType = firmware.SignChallengeAndSealSeksResult
}

extension RecoveryAuthorizeLostApp: IOCommand {
    typealias FFIStateType = RecoveryAuthorizeLostAppResultState
    typealias ResultType = firmware.RecoveryAuthorizeLostAppResult
}

extension RecoveryAuthorizeLostHw: IOCommand {
    typealias FFIStateType = RecoveryAuthorizeLostHwResultState
    typealias ResultType = firmware.RecoveryAuthorizeLostHwResult
}

extension UpgradeAuthorizeW3: IOCommand {
    typealias FFIStateType = UpgradeAuthorizeW3ResultState
    typealias ResultType = firmware.UpgradeAuthorizeW3Result
}

extension EekRestorationUnseal: IOCommand {
    typealias FFIStateType = EekRestorationUnsealResultState
    typealias ResultType = EekRestorationUnsealResult
}

extension FullAccountCloudBackupRestoration: IOCommand {
    typealias FFIStateType = FullAccountCloudBackupRestorationResultState
    typealias ResultType = FullAccountCloudBackupRestorationResult
}

extension FullAccountCloudBackupRestorationContinue: IOCommand {
    typealias FFIStateType = FullAccountCloudBackupRestorationContinueResultState
    typealias ResultType = FullAccountCloudBackupRestorationContinueResult
}

extension SignTxRequest: IOCommand {
    typealias FFIStateType = SignTxRequestResultState
    typealias ResultType = SignTxRequestResult
}

extension SignStreamStart: IOCommand {
    typealias FFIStateType = SignStreamStartResultState
    typealias ResultType = SignStreamStartResult
}

extension SignStreamTransfer: IOCommand {
    typealias FFIStateType = SignStreamTransferResultState
    typealias ResultType = SignStreamTransferResult
}

extension SignStreamFinalize: IOCommand {
    typealias FFIStateType = SignStreamFinalizeResultState
    typealias ResultType = SignStreamFinalizeResult
}

extension GetTxSignature: IOCommand {
    typealias FFIStateType = TxSignatureState
    typealias ResultType = TxSignature
}

extension GetTxSignaturesBatch: IOCommand {
    typealias FFIStateType = TxSignaturesBatchState
    typealias ResultType = [TxSignature]
}

extension IOCommand {
    // These are defined ONCE per monomorphized result type

    func next(_ response: [UInt8]) throws -> IOResult<Bool> where FFIStateType == BooleanState {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<UInt16> where FFIStateType == U16State {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<String>
        where FFIStateType == PartiallySignedTransactionState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.FingerprintEnrollmentResult>
        where FFIStateType == firmware.FingerprintEnrollmentResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.UnlockInfo>
        where FFIStateType == UnlockInfoState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.EnrolledFingerprints>
        where FFIStateType == EnrolledFingerprintsState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<[UInt8]> where FFIStateType == BytesState {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<FwupFinishRspStatus>
        where FFIStateType == FwupFinishRspStatusState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.FirmwareMetadata>
        where FFIStateType == firmware.FirmwareMetadataState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<DeviceIdentifiers>
        where FFIStateType == DeviceIdentifiersState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.EventFragment>
        where FFIStateType == EventFragmentState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.TelemetryIdentifiers>
        where FFIStateType == TelemetryIdentifiersState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<[firmware.FirmwareFeatureFlagCfg]>
        where FFIStateType == firmware.FirmwareFeatureFlagsState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.CoredumpFragment>
        where FFIStateType == CoredumpFragmentState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.PublicKey>
        where FFIStateType == PublicKeyState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.DescriptorPublicKey>
        where FFIStateType == DescriptorPublicKeyState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.Signature>
        where FFIStateType == SignatureState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.DeviceInfo>
        where FFIStateType == DeviceInfoState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<WipeStateResult>
        where FFIStateType == WipeStateResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<FwupStartResult>
        where FFIStateType == FwupStartResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<ConfirmedCommandResult>
        where FFIStateType == ConfirmedCommandResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<GetAddressResult>
        where FFIStateType == GetAddressResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<SignStartResult>
        where FFIStateType == SignStartResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<SignTransferResult>
        where FFIStateType == SignTransferResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<SignActionProofResult>
        where FFIStateType == SignActionProofResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<LostAppRecoveryResult>
        where FFIStateType == LostAppRecoveryResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<LostAppRecoveryContinueResult>
        where FFIStateType == LostAppRecoveryContinueResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<LostAppRecoverySignChallengeResult>
        where FFIStateType == LostAppRecoverySignChallengeResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<RotateAppAuthKeysResult>
        where FFIStateType == RotateAppAuthKeysResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.UpgradeRotateAppAuthKeysResult>
        where FFIStateType == UpgradeRotateAppAuthKeysResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.SignChallengeAndSealSeksResult>
        where FFIStateType == SignChallengeAndSealSeksResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.RecoveryAuthorizeLostAppResult>
        where FFIStateType == RecoveryAuthorizeLostAppResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.RecoveryAuthorizeLostHwResult>
        where FFIStateType == RecoveryAuthorizeLostHwResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<firmware.UpgradeAuthorizeW3Result>
        where FFIStateType == UpgradeAuthorizeW3ResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<EekRestorationUnsealResult>
        where FFIStateType == EekRestorationUnsealResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<FullAccountCloudBackupRestorationResult>
        where FFIStateType == FullAccountCloudBackupRestorationResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws
        -> IOResult<FullAccountCloudBackupRestorationContinueResult>
        where FFIStateType == FullAccountCloudBackupRestorationContinueResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<SignTxRequestResult>
        where FFIStateType == SignTxRequestResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<SignStreamStartResult>
        where FFIStateType == SignStreamStartResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<SignStreamTransferResult>
        where FFIStateType == SignStreamTransferResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<SignStreamFinalizeResult>
        where FFIStateType == SignStreamFinalizeResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<TxSignature>
        where FFIStateType == TxSignatureState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<[TxSignature]>
        where FFIStateType == TxSignaturesBatchState
    {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }
}

// MARK: - NFC command driver

/// Commands that are executed frequently and should not produce start/success/failure logs.
private let quietNFCCommands: Set<ObjectIdentifier> = [
    ObjectIdentifier(SignTxRequest.self),
    ObjectIdentifier(SignStreamStart.self),
    ObjectIdentifier(SignStreamTransfer.self),
    ObjectIdentifier(SignStreamFinalize.self),
    ObjectIdentifier(SignTransaction.self),
]

extension IOCommand {
    private var isQuiet: Bool {
        quietNFCCommands.contains(ObjectIdentifier(type(of: self)))
    }

    @discardableResult func transceive(session: NfcSession) async throws -> ResultType {
        if !isQuiet {
            log(tag: "NFC") { "NFC Command \(self) started" }
        }

        var data: [KotlinUByte] = []
        while true {
            do {
                switch try next(data.map(\.uint8Value)) {
                case let .data(response: response):
                    let buffer = response.map { KotlinUByte(value: $0) }
                    data = try await NfcResponseChainingKt.transceiveWithChaining(session, buffer: buffer)

                case let .result(value: value):
                    if !isQuiet {
                        log(tag: "NFC") { "NFC Command \(self) succeeded" }
                    }
                    return value
                }
            } catch {
                if !isQuiet {
                    log(.warn, tag: "NFC", error: error) { "NFC Command \(self) failed" }
                }
                switch error {
                case CommandError.InProgress:
                    throw NfcException.ConfirmationPending().asError()

                case CommandError.UserDenied:
                    throw NfcException.UserDenied().asError()

                case CommandError.Unauthenticated:
                    throw NfcException.CommandErrorUnauthenticated().asError()

                case CommandError.FeatureNotSupported:
                    throw NfcException.FeatureNotSupported().asError()

                case CommandError.SealCsekResponseUnsealError:
                    throw NfcException.CommandErrorSealCsekResponseUnsealException().asError()

                case CommandError.FileNotFound:
                    throw NfcException.CommandErrorFileNotFound().asError()

                case CommandError.DescriptorNotLoaded:
                    throw NfcException.DescriptorNotLoaded().asError()

                case CommandError.ConfirmationNotCompleted:
                    throw NfcException.ConfirmationNotCompleted().asError()

                // If there was an issue with the specific command, it will be thrown as a
                // `CommandError`
                case is CommandError:
                    throw NfcException.CommandError(message: error.localizedDescription, cause: nil)
                        .asError()

                // Otherwise, if there was an issue with the session in general (like a timeout or
                // cancellation),
                // that will be thrown by the session and already mapped to an NfcException in
                // NfcSession.transceive,
                // so just return the error
                default:
                    throw error
                }
            }
        }
    }
}

enum NFCSessionError: Error {
    case InvalidAPDU
}
