import core
import CoreNFC
import Shared

public final class NfcCommandsImpl: NfcCommands {

    public func fwupStart(
        session: NfcSession,
        patchSize: KotlinUInt?,
        fwupMode: Shared.FwupMode
    ) async throws -> KotlinBoolean {
        return .init(bool: try await FwupStart(
            patchSize: patchSize?.uint32Value,
            fwupMode: fwupMode.toCoreFwupMode()
        ).transceive(session: session))
    }

    public func fwupTransfer(
        session: NfcSession,
        sequenceId: UInt32,
        fwupData: [KotlinUByte],
        offset: UInt32,
        fwupMode: Shared.FwupMode
    ) async throws -> KotlinBoolean {
        return .init(bool: try await FwupTransfer(
            sequenceId: sequenceId,
            fwupData: fwupData.map { $0.uint8Value },
            offset: offset,
            fwupMode: fwupMode.toCoreFwupMode()
        ).transceive(session: session))
    }

    public func fwupFinish(
        session: NfcSession,
        appPropertiesOffset: UInt32,
        signatureOffset: UInt32,
        fwupMode: Shared.FwupMode
    ) async throws -> FwupFinishResponseStatus {
        return try await FwupFinish(
            appPropertiesOffset: appPropertiesOffset,
            signatureOffset: signatureOffset,
            fwupMode: fwupMode.toCoreFwupMode()
        ).transceive(session: session).toFwupFinishResponseStatus()
    }

    public func getAuthenticationKey(session: NfcSession) async throws -> HwAuthPublicKey {
        return .init(pubKey: .init(value: try await GetAuthenticationKey()
            .transceive(session: session)))
    }

    public func getCoredumpCount(session: NfcSession) async throws -> KotlinInt {
        return .init(int: Int32(try await GetCoredumpCount().transceive(session: session)))
    }

    public func getCoredumpFragment(session: NfcSession, offset: Int32) async throws -> Shared.CoredumpFragment {
        let fragment = try await GetCoredumpFragment(offset: UInt32(offset)).transceive(session: session)
        return .init(
            data: fragment.data.map { KotlinUByte(value: $0) },
            offset: fragment.offset,
            complete: fragment.complete,
            coredumpsRemaining: fragment.coredumpsRemaining
        )
    }

    public func getDeviceInfo(session: NfcSession) async throws -> Shared.FirmwareDeviceInfo {
        return .init(coreDeviceInfo: try await GetDeviceInfo()
            .transceive(session: session))
    }

    public func getEvents(session: NfcSession) async throws -> Shared.EventFragment {
        let events = try await GetEvents().transceive(session: session)
        return .init(fragment: events.fragment.map { KotlinUByte(value: $0) }, remainingSize: events.remainingSize)
    }

    public func getFingerprintEnrollmentStatus(session: NfcSession) async throws -> Shared.FingerprintEnrollmentStatus {
        let status = try await GetFingerprintEnrollmentStatus().transceive(session: session)
        switch status {
        case .statusUnspecified:
            return .unspecified
        case .incomplete:
            return .incomplete
        case .complete:
            return .complete
        case .notInProgress:
            return .notInProgress
        }
    }
    
    public func deleteFingerprint(session: NfcSession, index: Int32) async throws -> KotlinBoolean {
        return .init(bool: try await DeleteFingerprint(
            index: UInt32(index)
        ).transceive(session: session))
    }
    
    public func getEnrolledFingerprints(session: NfcSession) async throws -> Shared.EnrolledFingerprints {
        return try await GetEnrolledFingerprints().transceive(session: session).toSharedEnrolledFingerprints()
    }
    
    public func getUnlockMethod(session: NfcSession) async throws -> Shared.UnlockInfo {
        return try await GetUnlockMethod().transceive(session: session).toSharedUnlockInfo()
    }
    
    public func setFingerprintLabel(session: NfcSession, fingerprintHandle: Shared.FingerprintHandle) async throws -> KotlinBoolean {
        return .init(bool: try await SetFingerprintLabel(
            index: UInt32(fingerprintHandle.index),
            label: fingerprintHandle.label
                                                        ).transceive(session: session))
    }
    

    public func getFirmwareMetadata(session: NfcSession) async throws -> Shared.FirmwareMetadata {
        return .init(coreMetadata: try await GetFirmwareMetadata()
            .transceive(session: session))
    }

    public func getInitialSpendingKey(session: NfcSession, network: BitcoinNetworkType) async throws -> HwSpendingPublicKey {
        return .init(dpub: try await GetInitialSpendingKey(network: network.btcNetwork)
            .transceive(session: session))
    }

    public func getNextSpendingKey(session: NfcSession, existingDescriptorPublicKeys: [HwSpendingPublicKey], network: BitcoinNetworkType) async throws -> HwSpendingPublicKey {
        return .init(dpub: try await GetNextSpendingKey(
            existing: existingDescriptorPublicKeys.map { $0.key.dpub },
            network: network.btcNetwork
        ).transceive(session: session))
    }

    public func lockDevice(session: NfcSession) async throws -> KotlinBoolean {
        return .init(bool: try await LockDevice().transceive(session: session))
    }

    public func queryAuthentication(session: NfcSession) async throws -> KotlinBoolean {
        return .init(bool: try await QueryAuthentication()
            .transceive(session: session))
    }

    public func sealKey(session: NfcSession, unsealedKey: Csek) async throws -> OkioByteString {
        let unsealedKey = unsealedKey.key.raw.toByteArray().asUInt8Array()
        let sealedKey = try await SealKey(unsealedKey: unsealedKey)
            .transceive(session: session)
        return OkioKt.ByteString(data: Data(sealedKey))
    }

    public func signChallenge(session: NfcSession, challenge: OkioByteString) async throws -> String {
        return try await SignChallenge(challenge: challenge.toByteArray().asUInt8Array())
            .transceive(session: session)
    }

    public func signTransaction(session: NfcSession, psbt: Psbt, spendingKeyset: SpendingKeyset) async throws -> Psbt {
        return .init(
            id: psbt.id,
            base64: try await SignTransaction(serializedPsbt: psbt.base64).transceive(session: session),
            fee: psbt.fee,
            baseSize: psbt.baseSize,
            numOfInputs: psbt.numOfInputs,
            amountSats: psbt.amountSats
        )
    }

    public func startFingerprintEnrollment(session: NfcSession, fingerprintHandle: Shared.FingerprintHandle) async throws -> KotlinBoolean {
        return .init(bool: try await StartFingerprintEnrollment(
            index: UInt32(fingerprintHandle.index), label: fingerprintHandle.label
        ).transceive(session: session))
    }

    public func unsealKey(session: NfcSession, sealedKey: [KotlinUByte]) async throws -> [KotlinUByte] {
        return try await UnsealKey(sealedKey: sealedKey.map { $0.uint8Value })
            .transceive(session: session)
            .map { KotlinUByte(value: $0)}
    }

    public func version(session: NfcSession) async throws -> KotlinUShort {
        return .init(unsignedShort: try await Version().transceive(session: session))
    }

    public func wipeDevice(session: NfcSession) async throws -> KotlinBoolean {
        return .init(bool: try await WipeState().transceive(session: session))
    }

    public func getFirmwareFeatureFlags(session: NfcSession) async throws -> [Shared.FirmwareFeatureFlagCfg] {
        return try await GetFirmwareFeatureFlags().transceive(session: session).map { $0.toSharedFirmwareFeatureFlagCfg() }
    }

    public func getCert(session: NfcSession, certType: FirmwareCertType) async throws -> [KotlinUByte] {
        return try await GetCert(kind: certType.toCoreCertType()).transceive(session: session)
            .map { KotlinUByte(value: $0) }
    }


    public func signVerifyAttestationChallenge(session: NfcSession, deviceIdentityDer: [KotlinUByte], challenge: [KotlinUByte]) async throws -> KotlinBoolean {
        return .init(bool: try await SignVerifyAttestationChallenge(
                        deviceIdentityDer: deviceIdentityDer.map { $0.uint8Value },
                        challenge: challenge.map { $0.uint8Value })
                .transceive(session: session))
    }
}

// MARK: -

private extension Shared.FwupMode {
    func toCoreFwupMode() -> core.FwupMode {
        switch self {
        case .delta: return .delta
        case .normal: return .normal
        default: return .normal
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

private extension core.FirmwareFeatureFlagCfg {
    func toSharedFirmwareFeatureFlagCfg() -> Shared.FirmwareFeatureFlagCfg {
        switch self.flag {
        case .deviceInfoFlag: return Shared.FirmwareFeatureFlagCfg(flag: Shared.FirmwareFeatureFlag.deviceInfoFlag, enabled: self.enabled)
        case .rateLimitTemplateUpdate: return Shared.FirmwareFeatureFlagCfg(flag: Shared.FirmwareFeatureFlag.rateLimitTemplateUpdate, enabled: self.enabled)
        case .telemetry: return Shared.FirmwareFeatureFlagCfg(flag: Shared.FirmwareFeatureFlag.telemetry, enabled: self.enabled)
        case .unlock: return Shared.FirmwareFeatureFlagCfg(flag: Shared.FirmwareFeatureFlag.unlock, enabled: self.enabled)
        case .multipleFingerprints : return Shared.FirmwareFeatureFlagCfg(flag: Shared.FirmwareFeatureFlag.multipleFingerprints, enabled: self.enabled)
        }
    }
}

private extension FirmwareCertType {
    func toCoreCertType() -> core.CertType {
        switch self {
        case .batch: return .batchCert
        case .identity: return .deviceHostCert
        default: return .deviceHostCert
        }
    }
}

private extension core.EnrolledFingerprints {
    func toSharedEnrolledFingerprints() -> Shared.EnrolledFingerprints {
        return Shared.EnrolledFingerprints(maxCount: Int32(self.maxCount),
                                           fingerprintHandles: self.fingerprints.map({ $0.toSharedFingerprintHandle()})
        )
    }
}

private extension core.UnlockInfo {
    func toSharedUnlockInfo() -> Shared.UnlockInfo {
        return Shared.UnlockInfo(unlockMethod: self.method.toSharedUnlockMethod(),
                                 fingerprintIdx: self.fingerprintIndex.map({ KotlinInt(int: Int32($0)) })
        )
    }
}

private extension core.UnlockMethod {
    func toSharedUnlockMethod() -> Shared.UnlockMethod {
        switch self {
        case .unspecified: return .unspecified
        case .biometrics: return .biometrics
        case .unlockSecret: return .unlockSecret
        }
    }
}

private extension core.FingerprintHandle {
    func toSharedFingerprintHandle() -> Shared.FingerprintHandle {
        return Shared.FingerprintHandle(index: Int32(self.index), label: self.label)
    }
}
