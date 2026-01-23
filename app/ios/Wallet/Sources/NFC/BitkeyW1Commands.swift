import CoreNFC
import firmware
import Shared

public final class BitkeyW1Commands: NfcCommands {

    public func fwupStart(
        session: NfcSession,
        patchSize: KotlinUInt?,
        fwupMode: Shared.FwupMode,
        mcuRole: Shared.McuRole = .core
    ) async throws -> Shared.HardwareInteraction {
        let result = try await FwupStart(
            patchSize: patchSize?.uint32Value,
            fwupMode: fwupMode.toCoreFwupMode(),
            mcuRole: mcuRole.toCoreMcuRole()
        ).transceive(session: session)
        switch result {
        case let .success(value):
            return Shared.HardwareInteractionCompleted<KotlinBoolean>(
                result: KotlinBoolean(bool: value)
            ) as Shared.HardwareInteraction
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let suspendFunction = NfcSessionSuspendFunction { newSession, commands in
                let confirmResult = try await commands.getConfirmationResult(
                    session: newSession,
                    handles: handles
                )
                switch confirmResult {
                case let fwupStartResult as Shared.ConfirmationResultFwupStart:
                    return Shared.HardwareInteractionCompleted<KotlinBoolean>(
                        result: KotlinBoolean(bool: fwupStartResult.success)
                    ) as Shared.HardwareInteraction
                default:
                    throw NfcException.CommandError(
                        message: "fwupStart expected FwupStart result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared.HardwareInteractionRequiresConfirmation<KotlinBoolean>(
                fetchResult: suspendFunction
            ) as Shared.HardwareInteraction
        }
    }

    public func fwupTransfer(
        session: NfcSession,
        sequenceId: UInt32,
        fwupData: [KotlinUByte],
        offset: UInt32,
        fwupMode: Shared.FwupMode,
        mcuRole: Shared.McuRole = .core
    ) async throws -> KotlinBoolean {
        return try await .init(bool: FwupTransfer(
            sequenceId: sequenceId,
            fwupData: fwupData.map(\.uint8Value),
            offset: offset,
            fwupMode: fwupMode.toCoreFwupMode(),
            mcuRole: mcuRole.toCoreMcuRole()
        ).transceive(session: session))
    }

    public func fwupFinish(
        session: NfcSession,
        appPropertiesOffset: UInt32,
        signatureOffset: UInt32,
        fwupMode: Shared.FwupMode,
        mcuRole: Shared.McuRole = .core
    ) async throws -> FwupFinishResponseStatus {
        return try await FwupFinish(
            appPropertiesOffset: appPropertiesOffset,
            signatureOffset: signatureOffset,
            fwupMode: fwupMode.toCoreFwupMode(),
            mcuRole: mcuRole.toCoreMcuRole()
        ).transceive(session: session).toFwupFinishResponseStatus()
    }

    public func getAuthenticationKey(session: NfcSession) async throws -> HwAuthPublicKey {
        return try await .init(pubKey: .init(
            value: GetAuthenticationKey()
                .transceive(session: session)
        ))
    }

    public func getCoredumpCount(session: NfcSession) async throws -> KotlinInt {
        return try await .init(int: Int32(GetCoredumpCount().transceive(session: session)))
    }

    public func getCoredumpFragment(
        session: NfcSession,
        offset: Int32,
        mcuRole: Shared.McuRole
    ) async throws -> Shared
        .CoredumpFragment
    {
        let fragment = try await GetCoredumpFragment(
            offset: UInt32(offset),
            mcuRole: mcuRole.toCoreMcuRole()
        )
        .transceive(session: session)
        return .init(
            data: fragment.data.map { KotlinUByte(value: $0) },
            offset: fragment.offset,
            complete: fragment.complete,
            coredumpsRemaining: fragment.coredumpsRemaining,
            mcuRole: {
                switch fragment.mcuRole {
                case .core: return .core
                case .uxc: return .uxc
                case .none: return nil
                }
            }() as Shared.McuRole?,
            mcuName: {
                switch fragment.mcuName {
                case .efr32: return .efr32
                case .stm32u5: return .stm32u5
                case .none: return nil
                }
            }() as Shared.McuName?,
        )
    }

    public func getDeviceInfo(session: NfcSession) async throws -> Shared.FirmwareDeviceInfo {
        return try await .init(
            coreDeviceInfo: GetDeviceInfo()
                .transceive(session: session)
        )
    }

    public func getEvents(
        session: NfcSession,
        mcuRole: Shared.McuRole
    ) async throws -> Shared.EventFragment {
        let events = try await GetEvents(mcuRole: mcuRole.toCoreMcuRole())
            .transceive(session: session)
        return .init(
            fragment: events.fragment.map { KotlinUByte(value: $0) },
            remainingSize: events.remainingSize,
            mcuRole: {
                switch events.mcuRole {
                case .core: return .core
                case .uxc: return .uxc
                case .none: return nil
                }
            }() as Shared.McuRole?,
        )
    }

    public func getFingerprintEnrollmentStatus(
        session: NfcSession,
        isEnrollmentContextAware: Bool
    ) async throws -> Shared.FingerprintEnrollmentResult {
        let result =
            try await GetFingerprintEnrollmentStatus(
                isEnrollmentContextAware: isEnrollmentContextAware
            )
            .transceive(session: session)

        let status: Shared.FingerprintEnrollmentStatus = {
            switch result.status {
            case .statusUnspecified:
                return .unspecified
            case .incomplete:
                return .incomplete
            case .complete:
                return .complete
            case .notInProgress:
                return .notInProgress
            }
        }()

        let diagnostics: Shared.FingerprintEnrollmentDiagnostics? = if let diag = result
            .diagnostics
        {
            Shared.FingerprintEnrollmentDiagnostics(
                fingerCoverageValid: diag.fingerCoverageValid,
                fingerCoverage: Int32(diag.fingerCoverage),
                commonModeNoiseValid: diag.commonModeNoiseValid,
                commonModeNoise: Int32(diag.commonModeNoise),
                imageQualityValid: diag.imageQualityValid,
                imageQuality: Int32(diag.imageQuality),
                sensorCoverageValid: diag.sensorCoverageValid,
                sensorCoverage: Int32(diag.sensorCoverage),
                templateDataUpdateValid: diag.templateDataUpdateValid,
                templateDataUpdate: Int32(diag.templateDataUpdate)
            )
        } else {
            nil
        }

        return Shared.FingerprintEnrollmentResult(
            status: status,
            passCount: result.passCount.map { KotlinUInt(value: $0) },
            failCount: result.failCount.map { KotlinUInt(value: $0) },
            diagnostics: diagnostics
        )
    }

    public func deleteFingerprint(session: NfcSession, index: Int32) async throws -> KotlinBoolean {
        return try await .init(bool: DeleteFingerprint(
            index: UInt32(index)
        ).transceive(session: session))
    }

    public func getEnrolledFingerprints(session: NfcSession) async throws -> Shared
        .EnrolledFingerprints
    {
        return try await GetEnrolledFingerprints().transceive(session: session)
            .toSharedEnrolledFingerprints()
    }

    public func getUnlockMethod(session: NfcSession) async throws -> Shared.UnlockInfo {
        return try await GetUnlockMethod().transceive(session: session).toSharedUnlockInfo()
    }

    public func setFingerprintLabel(
        session: NfcSession,
        fingerprintHandle: Shared.FingerprintHandle
    ) async throws -> KotlinBoolean {
        return try await .init(bool: SetFingerprintLabel(
            index: UInt32(fingerprintHandle.index),
            label: fingerprintHandle.label
        ).transceive(session: session))
    }

    public func cancelFingerprintEnrollment(session: NfcSession) async throws -> KotlinBoolean {
        return try await .init(bool: CancelFingerprintEnrollment().transceive(session: session))
    }

    public func getFirmwareMetadata(session: NfcSession) async throws -> Shared.FirmwareMetadata {
        return try await .init(
            coreMetadata: GetFirmwareMetadata()
                .transceive(session: session)
        )
    }

    public func getInitialSpendingKey(
        session: NfcSession,
        network: BitcoinNetworkType
    ) async throws -> HwSpendingPublicKey {
        return try await .init(
            dpub: GetInitialSpendingKey(network: network.btcNetwork)
                .transceive(session: session)
        )
    }

    public func getNextSpendingKey(
        session: NfcSession,
        existingDescriptorPublicKeys: [HwSpendingPublicKey],
        network: BitcoinNetworkType
    ) async throws -> HwSpendingPublicKey {
        return try await .init(dpub: GetNextSpendingKey(
            existing: existingDescriptorPublicKeys.map(\.key.dpub),
            network: network.btcNetwork
        ).transceive(session: session))
    }

    public func lockDevice(session: NfcSession) async throws -> KotlinBoolean {
        return try await .init(bool: LockDevice().transceive(session: session))
    }

    public func queryAuthentication(session: NfcSession) async throws -> KotlinBoolean {
        return try await .init(
            bool: QueryAuthentication()
                .transceive(session: session)
        )
    }

    public func sealData(
        session: NfcSession,
        unsealedData: OkioByteString
    ) async throws -> OkioByteString {
        let sealedData = try await SealKey(unsealedKey: unsealedData.toByteArray().asUInt8Array())
            .transceive(session: session)
        return OkioKt.ByteString(data: Data(sealedData))
    }

    public func unsealData(
        session: NfcSession,
        sealedData: OkioByteString
    ) async throws -> OkioByteString {
        return try await OkioKt
            .ByteString(data: Data(
                UnsealKey(
                    sealedKey: sealedData.toByteArray()
                        .asUInt8Array()
                )
                .transceive(session: session)
            ))
    }

    public func signChallenge(
        session: NfcSession,
        challenge: OkioByteString
    ) async throws -> String {
        return try await SignChallenge(
            challenge: challenge.toByteArray().asUInt8Array(),
            asyncSign: session.parameters.asyncNfcSigning
        )
        .transceive(session: session)
    }

    public func signTransaction(
        session: NfcSession,
        psbt: Psbt,
        spendingKeyset: SpendingKeyset
    ) async throws -> Shared.HardwareInteraction {
        let signedPsbt = try await Psbt(
            id: psbt.id,
            base64: SignTransaction(
                serializedPsbt: psbt.base64,
                originFingerprint: spendingKeyset.hardwareKey.key.origin.fingerprint,
                asyncSign: session.parameters.asyncNfcSigning
            ).transceive(session: session),
            fee: psbt.fee,
            baseSize: psbt.baseSize,
            numOfInputs: psbt.numOfInputs,
            amountSats: psbt.amountSats,
            inputs: psbt.inputs,
            outputs: psbt.outputs
        )
        return Shared.HardwareInteractionCompleted<Psbt>(result: signedPsbt) as Shared
            .HardwareInteraction
    }

    public func startFingerprintEnrollment(
        session: NfcSession,
        fingerprintHandle: Shared.FingerprintHandle
    ) async throws -> KotlinBoolean {
        return try await .init(bool: StartFingerprintEnrollment(
            index: UInt32(fingerprintHandle.index), label: fingerprintHandle.label
        ).transceive(session: session))
    }

    public func version(session: NfcSession) async throws -> KotlinUShort {
        return try await .init(unsignedShort: Version().transceive(session: session))
    }

    public func wipeDevice(session: NfcSession) async throws -> Shared.HardwareInteraction {
        let result = try await WipeState().transceive(session: session)
        switch result {
        case let .success(value):
            return Shared.HardwareInteractionCompleted<KotlinBoolean>(
                result: KotlinBoolean(bool: value)
            ) as Shared.HardwareInteraction
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let suspendFunction = NfcSessionSuspendFunction { newSession, commands in
                let confirmResult = try await commands.getConfirmationResult(
                    session: newSession,
                    handles: handles
                )
                guard let wipeResult = confirmResult as? Shared.ConfirmationResultWipeDevice else {
                    throw NfcException.CommandError(
                        message: "wipeDevice expected WipeDevice result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
                return Shared.HardwareInteractionCompleted<KotlinBoolean>(
                    result: KotlinBoolean(bool: wipeResult.success)
                ) as Shared.HardwareInteraction
            }
            return Shared.HardwareInteractionRequiresConfirmation<KotlinBoolean>(
                fetchResult: suspendFunction
            ) as Shared.HardwareInteraction
        }
    }

    public func getFirmwareFeatureFlags(session: NfcSession) async throws
        -> [Shared.FirmwareFeatureFlagCfg]
    {
        return try await GetFirmwareFeatureFlags().transceive(session: session)
            .map { $0.toSharedFirmwareFeatureFlagCfg() }
    }

    public func getCert(
        session: NfcSession,
        certType: FirmwareCertType
    ) async throws -> [KotlinUByte] {
        return try await GetCert(kind: certType.toCoreCertType()).transceive(session: session)
            .map { KotlinUByte(value: $0) }
    }

    public func signVerifyAttestationChallenge(
        session: NfcSession,
        deviceIdentityDer: [KotlinUByte],
        challenge: [KotlinUByte]
    ) async throws -> KotlinBoolean {
        return try await .init(
            bool: SignVerifyAttestationChallenge(
                deviceIdentityDer: deviceIdentityDer.map(\.uint8Value),
                challenge: challenge.map(\.uint8Value)
            )
            .transceive(session: session)
        )
    }

    public func getGrantRequest(
        session: NfcSession,
        action: Shared.GrantAction
    ) async throws -> Shared.GrantRequest {
        switch action {
        case Shared.GrantAction.fingerprintReset:
            let grantRequestBytes = try await FingerprintResetRequest().transceive(session: session)
            guard let grantRequest = Shared.GrantRequest.Companion()
                .fromBytes(bytes: OkioKt.ByteString(data: Data(grantRequestBytes)))
            else {
                throw GrantRequestError.parsingFailed
            }
            return grantRequest
        default:
            throw GrantRequestError.unsupportedAction(action.description())
        }
    }

    public func provideGrant(
        session: NfcSession,
        grant: Shared.Grant
    ) async throws -> KotlinBoolean {
        guard let grantPayloadBytes = grant.toBytes()?.toByteArray().asUInt8Array() else {
            fatalError("Failed to serialize Grant for device command")
        }
        do {
            return try await .init(
                bool: FingerprintResetFinalize(
                    grantPayload: grantPayloadBytes
                )
                .transceive(session: session)
            )
        } catch {
            // For either of these specific errors, the grant has likely already been used
            let nsError = error as NSError
            if nsError.isKotlinNfcFileNotFoundError() {
                // specific error from newer firmware
                return .init(bool: false)
            } else if nsError.isKotlinNfcGeneralError() {
                // fallback for older firmware
                return .init(bool: false)
            } else {
                // rethrow other command errors
                throw error
            }
        }
    }

    public func provisionAppAuthKey(
        session: NfcSession,
        appAuthKey: OkioByteString
    ) async throws -> KotlinBoolean {
        return try await .init(
            bool: ProvisionAppAuthKey(pubkey: appAuthKey.toByteArray().asUInt8Array())
                .transceive(session: session)
        )
    }

    public func getConfirmationResult(
        session: NfcSession,
        handles: Shared.ConfirmationHandles
    ) async throws -> Shared.ConfirmationResult {
        let result = try await GetConfirmationResult(
            responseHandle: handles.responseHandle.map(\.uint8Value),
            confirmationHandle: handles.confirmationHandle.map(\.uint8Value)
        ).transceive(session: session)
        switch result {
        case let .wipeState(success):
            return Shared.ConfirmationResultWipeDevice(success: success)
        case let .fwupStart(success):
            return Shared.ConfirmationResultFwupStart(success: success)
        }
    }
}

// MARK: -

private enum GrantRequestError: LocalizedError {
    case unsupportedAction(String)
    case parsingFailed

    public var errorDescription: String? {
        switch self {
        case let .unsupportedAction(actionName):
            return "Unsupported GrantAction: \(actionName)"
        case .parsingFailed:
            return "Failed to parse GrantRequest from device response"
        }
    }
}

private extension Shared.FwupMode {
    func toCoreFwupMode() -> firmware.FwupMode {
        switch self {
        case .delta: return .delta
        case .normal: return .normal
        default: return .normal
        }
    }
}

private extension Shared.McuRole {
    func toCoreMcuRole() -> firmware.McuRole {
        switch self {
        case .core: return .core
        case .uxc: return .uxc
        default: return .core
        }
    }
}

private extension FwupFinishRspStatus {
    func toFwupFinishResponseStatus() -> FwupFinishResponseStatus {
        switch self {
        case .error: return .error
        case .signatureInvalid: return .signatureinvalid
        case .success: return .success
        case .unauthenticated: return .unauthenticated
        case .unspecified: return .unspecified
        case .versionInvalid: return .versioninvalid
        case .willApplyPatch: return .willapplypatch
        }
    }
}

private extension firmware.FirmwareFeatureFlagCfg {
    func toSharedFirmwareFeatureFlagCfg() -> Shared.FirmwareFeatureFlagCfg {
        switch self.flag {
        case .deviceInfoFlag: return Shared.FirmwareFeatureFlagCfg(
                flag: Shared.FirmwareFeatureFlag.deviceInfoFlag,
                enabled: self.enabled
            )
        case .rateLimitTemplateUpdate: return Shared.FirmwareFeatureFlagCfg(
                flag: Shared.FirmwareFeatureFlag.rateLimitTemplateUpdate,
                enabled: self.enabled
            )
        case .telemetry: return Shared.FirmwareFeatureFlagCfg(
                flag: Shared.FirmwareFeatureFlag.telemetry,
                enabled: self.enabled
            )
        case .unlock: return Shared.FirmwareFeatureFlagCfg(
                flag: Shared.FirmwareFeatureFlag.unlock,
                enabled: self.enabled
            )
        case .multipleFingerprints: return Shared.FirmwareFeatureFlagCfg(
                flag: Shared.FirmwareFeatureFlag.multipleFingerprints,
                enabled: self.enabled
            )
        case .improvedFingerprintEnrollment: return Shared.FirmwareFeatureFlagCfg(
                flag: Shared.FirmwareFeatureFlag.improvedFingerprintEnrollment,
                enabled: self.enabled
            )
        case .asyncSigning: return Shared.FirmwareFeatureFlagCfg(
                flag: Shared.FirmwareFeatureFlag.asyncSigning,
                enabled: self.enabled
            )
        case .signingOptimizations: return Shared.FirmwareFeatureFlagCfg(
                flag: Shared.FirmwareFeatureFlag.signingOptimizations,
                enabled: self.enabled
            )
        case .fingerprintReset: return Shared.FirmwareFeatureFlagCfg(
                flag: Shared.FirmwareFeatureFlag.fingerprintReset,
                enabled: self.enabled
            )
        }
    }
}

private extension FirmwareCertType {
    func toCoreCertType() -> firmware.CertType {
        switch self {
        case .batch: return .batchCert
        case .identity: return .deviceHostCert
        default: return .deviceHostCert
        }
    }
}

private extension firmware.EnrolledFingerprints {
    func toSharedEnrolledFingerprints() -> Shared.EnrolledFingerprints {
        return Shared.EnrolledFingerprints(
            maxCount: Int32(self.maxCount),
            fingerprintHandles: self.fingerprints
                .map { $0.toSharedFingerprintHandle() }
        )
    }
}

private extension firmware.UnlockInfo {
    func toSharedUnlockInfo() -> Shared.UnlockInfo {
        return Shared.UnlockInfo(
            unlockMethod: self.method.toSharedUnlockMethod(),
            fingerprintIdx: self.fingerprintIndex
                .map { KotlinInt(int: Int32($0)) }
        )
    }
}

private extension firmware.UnlockMethod {
    func toSharedUnlockMethod() -> Shared.UnlockMethod {
        switch self {
        case .unspecified: return .unspecified
        case .biometrics: return .biometrics
        case .unlockSecret: return .unlockSecret
        }
    }
}

private extension firmware.FingerprintHandle {
    func toSharedFingerprintHandle() -> Shared.FingerprintHandle {
        return Shared.FingerprintHandle(index: Int32(self.index), label: self.label)
    }
}
