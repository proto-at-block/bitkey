import core
import CoreNFC
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
    typealias FFIStateType = core.FingerprintEnrollmentStatusState
    typealias ResultType = core.FingerprintEnrollmentStatus
}

extension SignTransaction: IOCommand {
    typealias FFIStateType = PartiallySignedTransactionState
    typealias ResultType = String
}

extension WipeState: IOCommand {
    typealias FFIStateType = BooleanState
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
    typealias ResultType = core.FirmwareMetadata
}

extension GetDeviceIdentifiers: IOCommand {
    typealias FFIStateType = DeviceIdentifiersState
    typealias ResultType = DeviceIdentifiers
}

extension GetEvents: IOCommand {
    typealias FFIStateType = EventFragmentState
    typealias ResultType = core.EventFragment
}

extension GetTelemetryIdentifiers: IOCommand {
    typealias FFIStateType = TelemetryIdentifiersState
    typealias ResultType = core.TelemetryIdentifiers
}

extension GetFirmwareFeatureFlags: IOCommand {
    typealias FFIStateType = core.FirmwareFeatureFlagsState
    typealias ResultType = [core.FirmwareFeatureFlagCfg]
}

extension GetCoredumpCount: IOCommand {
    typealias FFIStateType = U16State
    typealias ResultType = UInt16
}

extension GetCoredumpFragment: IOCommand {
    typealias FFIStateType = CoredumpFragmentState
    typealias ResultType = core.CoredumpFragment
}

extension GetAuthenticationKey: IOCommand {
    typealias FFIStateType = PublicKeyState
    typealias ResultType = core.PublicKey
}

extension GetInitialSpendingKey: IOCommand {
    typealias FFIStateType = DescriptorPublicKeyState
    typealias ResultType = core.DescriptorPublicKey
}

extension GetNextSpendingKey: IOCommand {
    typealias FFIStateType = DescriptorPublicKeyState
    typealias ResultType = core.DescriptorPublicKey
}

extension SignChallenge: IOCommand {
    typealias FFIStateType = SignatureState
    typealias ResultType = Signature
}

extension GetDeviceInfo: IOCommand {
    typealias FFIStateType = DeviceInfoState
    typealias ResultType = core.DeviceInfo
}

extension GetCert: IOCommand {
    typealias FFIStateType = BytesState
    typealias ResultType = [UInt8]
}

extension SignVerifyAttestationChallenge: IOCommand {
    typealias FFIStateType = BooleanState
    typealias ResultType = Bool
}

extension IOCommand {
    // These are defined ONCE per monomorphized result type

    func next(_ response: [UInt8]) throws -> IOResult<Bool> where FFIStateType == BooleanState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<UInt16> where FFIStateType == U16State {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<String> where FFIStateType == PartiallySignedTransactionState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<core.FingerprintEnrollmentStatus> where FFIStateType == core.FingerprintEnrollmentStatusState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<[UInt8]> where FFIStateType == BytesState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<FwupFinishRspStatus> where FFIStateType == FwupFinishRspStatusState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<core.FirmwareMetadata> where FFIStateType == core.FirmwareMetadataState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<DeviceIdentifiers> where FFIStateType == DeviceIdentifiersState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }

    func next(_ response: [UInt8]) throws -> IOResult<core.EventFragment> where FFIStateType == EventFragmentState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<core.TelemetryIdentifiers> where FFIStateType == TelemetryIdentifiersState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<[core.FirmwareFeatureFlagCfg]> where FFIStateType == core.FirmwareFeatureFlagsState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<core.CoredumpFragment> where FFIStateType == CoredumpFragmentState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<core.PublicKey> where FFIStateType == PublicKeyState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<core.DescriptorPublicKey> where FFIStateType == DescriptorPublicKeyState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<core.Signature> where FFIStateType == SignatureState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
        }
    }
    
    func next(_ response: [UInt8]) throws -> IOResult<core.DeviceInfo> where FFIStateType == DeviceInfoState {
        switch try self.next(response: response) {
        case .data(response: let response): return .data(response: response)
        case .result(value: let value): return .result(value: value)
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
                switch try next(data.map { $0.uint8Value }) {
                case .data(response: let response):
                    let buffer = response.map { KotlinUByte(value: $0) }
                    data = try await session.transceive(buffer: buffer)

                case .result(value: let value):
                    log(tag: "NFC") { "NFC Command \(self) succeeded" }
                    return value
                }
            } catch {
                log(.warn, tag: "NFC", error: error) { "NFC Command \(self) failed" }
                switch error {
                case CommandError.Unauthenticated:
                    throw NfcException.CommandErrorUnauthenticated().asError()

                // If there was an issue with the specific command, it will be thrown as a `CommandError`
                case is CommandError:
                    throw NfcException.CommandError(message: error.localizedDescription, cause: nil).asError()

                // Otherwise, if there was an issue with the session in general (like a timeout or cancellation),
                // that will be thrown by the session and already mapped to an NfcException in NfcSession.transceive,
                // so just return the error
                default:
                    throw error
                }
            }
        }
    }
}

enum NFCSessionError : Error {
    case InvalidAPDU
}
