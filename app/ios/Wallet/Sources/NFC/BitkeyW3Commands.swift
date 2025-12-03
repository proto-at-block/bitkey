import CoreNFC
import firmware
import Shared

/**
 * Provides overrides for W3 implementation of NFC Commands and delegates
 * to an existing implementation otherwise.
 */
public final class BitkeyW3Commands: NfcCommands {

    private let delegate: NfcCommands

    public init(delegate: NfcCommands) {
        self.delegate = delegate
    }

    public func fwupStart(
        session: NfcSession,
        patchSize: KotlinUInt?,
        fwupMode: Shared.FwupMode
    ) async throws -> KotlinBoolean {
        return try await delegate.fwupStart(
            session: session,
            patchSize: patchSize,
            fwupMode: fwupMode
        )
    }

    public func fwupTransfer(
        session: NfcSession,
        sequenceId: UInt32,
        fwupData: [KotlinUByte],
        offset: UInt32,
        fwupMode: Shared.FwupMode
    ) async throws -> KotlinBoolean {
        return try await delegate.fwupTransfer(
            session: session,
            sequenceId: sequenceId,
            fwupData: fwupData,
            offset: offset,
            fwupMode: fwupMode
        )
    }

    public func fwupFinish(
        session: NfcSession,
        appPropertiesOffset: UInt32,
        signatureOffset: UInt32,
        fwupMode: Shared.FwupMode
    ) async throws -> FwupFinishResponseStatus {
        return try await delegate.fwupFinish(
            session: session,
            appPropertiesOffset: appPropertiesOffset,
            signatureOffset: signatureOffset,
            fwupMode: fwupMode
        )
    }

    public func getAuthenticationKey(session: NfcSession) async throws -> HwAuthPublicKey {
        return try await delegate.getAuthenticationKey(session: session)
    }

    public func getCoredumpCount(session: NfcSession) async throws -> KotlinInt {
        return try await delegate.getCoredumpCount(session: session)
    }

    public func getCoredumpFragment(session: NfcSession, offset: Int32) async throws -> Shared
        .CoredumpFragment
    {
        return try await delegate.getCoredumpFragment(session: session, offset: offset)
    }

    public func getDeviceInfo(session: NfcSession) async throws -> Shared.FirmwareDeviceInfo {
        return try await delegate.getDeviceInfo(session: session)
    }

    public func getEvents(session: NfcSession) async throws -> Shared.EventFragment {
        return try await delegate.getEvents(session: session)
    }

    public func getFingerprintEnrollmentStatus(
        session: NfcSession,
        isEnrollmentContextAware: Bool
    ) async throws -> Shared.FingerprintEnrollmentResult {
        return try await delegate.getFingerprintEnrollmentStatus(
            session: session,
            isEnrollmentContextAware: isEnrollmentContextAware
        )
    }

    public func deleteFingerprint(session: NfcSession, index: Int32) async throws -> KotlinBoolean {
        return try await delegate.deleteFingerprint(session: session, index: index)
    }

    public func getEnrolledFingerprints(session: NfcSession) async throws -> Shared
        .EnrolledFingerprints
    {
        return try await delegate.getEnrolledFingerprints(session: session)
    }

    public func getUnlockMethod(session: NfcSession) async throws -> Shared.UnlockInfo {
        return try await delegate.getUnlockMethod(session: session)
    }

    public func setFingerprintLabel(
        session: NfcSession,
        fingerprintHandle: Shared.FingerprintHandle
    ) async throws -> KotlinBoolean {
        return try await delegate.setFingerprintLabel(
            session: session,
            fingerprintHandle: fingerprintHandle
        )
    }

    public func cancelFingerprintEnrollment(session: NfcSession) async throws -> KotlinBoolean {
        return try await delegate.cancelFingerprintEnrollment(session: session)
    }

    public func getFirmwareMetadata(session: NfcSession) async throws -> Shared.FirmwareMetadata {
        return try await delegate.getFirmwareMetadata(session: session)
    }

    public func getInitialSpendingKey(
        session: NfcSession,
        network: BitcoinNetworkType
    ) async throws -> HwSpendingPublicKey {
        return try await delegate.getInitialSpendingKey(session: session, network: network)
    }

    public func getNextSpendingKey(
        session: NfcSession,
        existingDescriptorPublicKeys: [HwSpendingPublicKey],
        network: BitcoinNetworkType
    ) async throws -> HwSpendingPublicKey {
        return try await delegate.getNextSpendingKey(
            session: session,
            existingDescriptorPublicKeys: existingDescriptorPublicKeys,
            network: network
        )
    }

    public func lockDevice(session: NfcSession) async throws -> KotlinBoolean {
        return try await delegate.lockDevice(session: session)
    }

    public func queryAuthentication(session: NfcSession) async throws -> KotlinBoolean {
        return try await delegate.queryAuthentication(session: session)
    }

    public func sealData(
        session: NfcSession,
        unsealedData: OkioByteString
    ) async throws -> OkioByteString {
        return try await delegate.sealData(session: session, unsealedData: unsealedData)
    }

    public func unsealData(
        session: NfcSession,
        sealedData: OkioByteString
    ) async throws -> OkioByteString {
        return try await delegate.unsealData(session: session, sealedData: sealedData)
    }

    public func signChallenge(
        session: NfcSession,
        challenge: OkioByteString
    ) async throws -> String {
        return try await delegate.signChallenge(session: session, challenge: challenge)
    }

    public func signTransaction(
        session: NfcSession,
        psbt: Psbt,
        spendingKeyset: SpendingKeyset
    ) async throws -> Psbt {
        return try await delegate.signTransaction(
            session: session,
            psbt: psbt,
            spendingKeyset: spendingKeyset
        )
    }

    public func startFingerprintEnrollment(
        session: NfcSession,
        fingerprintHandle: Shared.FingerprintHandle
    ) async throws -> KotlinBoolean {
        return try await delegate.startFingerprintEnrollment(
            session: session,
            fingerprintHandle: fingerprintHandle
        )
    }

    public func version(session: NfcSession) async throws -> KotlinUShort {
        return try await delegate.version(session: session)
    }

    public func wipeDevice(session: NfcSession) async throws -> KotlinBoolean {
        return try await delegate.wipeDevice(session: session)
    }

    public func getFirmwareFeatureFlags(session: NfcSession) async throws
        -> [Shared.FirmwareFeatureFlagCfg]
    {
        return try await delegate.getFirmwareFeatureFlags(session: session)
    }

    public func getCert(
        session: NfcSession,
        certType: FirmwareCertType
    ) async throws -> [KotlinUByte] {
        return try await delegate.getCert(session: session, certType: certType)
    }

    public func signVerifyAttestationChallenge(
        session: NfcSession,
        deviceIdentityDer: [KotlinUByte],
        challenge: [KotlinUByte]
    ) async throws -> KotlinBoolean {
        return try await delegate.signVerifyAttestationChallenge(
            session: session,
            deviceIdentityDer: deviceIdentityDer,
            challenge: challenge
        )
    }

    public func getGrantRequest(
        session: NfcSession,
        action: Shared.GrantAction
    ) async throws -> Shared.GrantRequest {
        return try await delegate.getGrantRequest(session: session, action: action)
    }

    public func provideGrant(
        session: NfcSession,
        grant: Shared.Grant
    ) async throws -> KotlinBoolean {
        return try await delegate.provideGrant(session: session, grant: grant)
    }

    public func provisionAppAuthKey(
        session: NfcSession,
        appAuthKey: OkioByteString
    ) async throws -> KotlinBoolean {
        return try await delegate.provisionAppAuthKey(session: session, appAuthKey: appAuthKey)
    }
}
