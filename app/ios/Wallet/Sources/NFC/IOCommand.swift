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
    typealias ResultType = Bool
}

extension LockDevice: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension QueryAuthentication: IOCommand {
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
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
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

extension IOCommand {
    // These are defined ONCE per monomorphized result type

    func next(_ response: [UInt8]) throws -> IOResult<Bool> where FFIStateType == BooleanState {
        switch try self.next(response: response) {
        case let .data(response: response): return .data(response: response)
        case let .result(value: value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<Bool>
        where FFIStateType == WipeStateResultState
    {
        switch try self.next(response: response) {
        case let .data(response: response):
            return .data(response: response)
        case let .result(value: value):
            switch value {
            case let .success(success):
                return .result(value: success)
            case .confirmationPending:
                fatalError(
                    "Confirmation pending not yet supported - firmware should not return this"
                )
            }
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
}

// MARK: - NFC command driver

extension IOCommand {
    @discardableResult func transceive(session: NfcSession) async throws -> ResultType {
        log(tag: "NFC") { "NFC Command \(self) started" }

        var data: [KotlinUByte] = []
        while true {
            do {
                switch try next(data.map(\.uint8Value)) {
                case let .data(response: response):
                    let buffer = response.map { KotlinUByte(value: $0) }
                    data = try await session.transceive(buffer: buffer)

                case let .result(value: value):
                    log(tag: "NFC") { "NFC Command \(self) succeeded" }
                    return value
                }
            } catch {
                log(.warn, tag: "NFC", error: error) { "NFC Command \(self) failed" }
                switch error {
                case CommandError.Unauthenticated:
                    throw NfcException.CommandErrorUnauthenticated().asError()

                case CommandError.SealCsekResponseUnsealError:
                    throw NfcException.CommandErrorSealCsekResponseUnsealException().asError()

                case CommandError.FileNotFound:
                    throw NfcException.CommandErrorFileNotFound().asError()

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
