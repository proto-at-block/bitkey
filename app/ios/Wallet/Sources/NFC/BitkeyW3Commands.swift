import CoreNFC
import firmware
import Shared

/**
 * Provides overrides for W3 implementation of NFC Commands and delegates
 * to an existing implementation otherwise.
 */
public final class BitkeyW3Commands: NfcCommands {

    /// Maximum chunk size for PSBT transfer, defined by nanopb max_size annotation
    /// on sign_transfer_cmd.chunk_data in wallet.proto.
    private static let psbtChunkSize = 452

    /// Maximum total size for chunked responses (1 MB).
    /// Safety limit to prevent infinite loops if firmware misbehaves.
    private static let maxChunkedResponseSize = 1_000_000

    private let delegate: NfcCommands

    public init(delegate: NfcCommands) {
        self.delegate = delegate
    }

    public func fwupStart(
        session: NfcSession,
        patchSize: KotlinUInt?,
        fwupMode: Shared.FwupMode,
        mcuRole: Shared.McuRole,
        version: String
    ) async throws -> Shared.HardwareInteraction {
        return try await delegate.fwupStart(
            session: session,
            patchSize: patchSize,
            fwupMode: fwupMode,
            mcuRole: mcuRole,
            version: version
        )
    }

    public func fwupTransfer(
        session: NfcSession,
        sequenceId: UInt32,
        fwupData: [KotlinUByte],
        offset: UInt32,
        fwupMode: Shared.FwupMode,
        mcuRole: Shared.McuRole
    ) async throws -> KotlinBoolean {
        return try await delegate.fwupTransfer(
            session: session,
            sequenceId: sequenceId,
            fwupData: fwupData,
            offset: offset,
            fwupMode: fwupMode,
            mcuRole: mcuRole
        )
    }

    public func fwupFinish(
        session: NfcSession,
        appPropertiesOffset: UInt32,
        signatureOffset: UInt32,
        fwupMode: Shared.FwupMode,
        mcuRole: Shared.McuRole
    ) async throws -> FwupFinishResponseStatus {
        return try await delegate.fwupFinish(
            session: session,
            appPropertiesOffset: appPropertiesOffset,
            signatureOffset: signatureOffset,
            fwupMode: fwupMode,
            mcuRole: mcuRole
        )
    }

    public func getAuthenticationKey(session: NfcSession) async throws -> HwAuthPublicKey {
        return try await delegate.getAuthenticationKey(session: session)
    }

    public func getCoredumpCount(session: NfcSession) async throws -> KotlinInt {
        return try await delegate.getCoredumpCount(session: session)
    }

    public func getCoredumpFragment(
        session: NfcSession,
        offset: Int32,
        mcuRole: Shared.McuRole
    ) async throws -> Shared.CoredumpFragment {
        return try await delegate.getCoredumpFragment(
            session: session,
            offset: offset,
            mcuRole: mcuRole
        )
    }

    public func getDeviceInfo(session: NfcSession) async throws -> Shared.FirmwareDeviceInfo {
        return try await delegate.getDeviceInfo(session: session)
    }

    public func getEvents(
        session: NfcSession,
        mcuRole: Shared.McuRole
    ) async throws -> Shared.EventFragment {
        return try await delegate.getEvents(session: session, mcuRole: mcuRole)
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

    /// Fetches all chunks of data from the device using the generic
    /// getConfirmationResultChunk command.
    ///
    /// Uses chunk_index for idempotent retry - if NFC transmission fails after firmware
    /// responds, the same chunk can be re-requested safely.
    ///
    /// - Parameters:
    ///   - session: The active NFC session
    ///   - commands: NFC commands interface
    ///   - handles: Confirmation handles from the pending operation
    ///   - expectedSize: Expected total size from ChunkedDataAvailable (for validation)
    /// - Returns: The reassembled data as an array of bytes
    /// - Throws: NfcException.CommandError if size exceeds safety limit or doesn't match expected
    private func fetchAllChunks(
        session: NfcSession,
        commands: NfcCommands,
        handles: Shared.ConfirmationHandles,
        expectedSize: UInt32
    ) async throws -> [UInt8] {
        var chunks: [UInt8] = []
        var chunkIndex: UInt32 = 0
        var isLast = false

        while !isLast {
            let chunkData = try await commands.getConfirmationResultChunk(
                session: session,
                handles: handles,
                chunkIndex: chunkIndex
            )
            chunks.append(contentsOf: chunkData.chunk.map(\.uint8Value))
            isLast = chunkData.isLast
            chunkIndex += 1

            // Safety limit to prevent infinite loops if firmware never sets isLast
            if chunks.count > Self.maxChunkedResponseSize {
                throw NfcException.CommandError(
                    message: "Chunked response exceeded maximum size of \(Self.maxChunkedResponseSize) bytes",
                    cause: nil
                ).asError()
            }
        }

        if chunks.count != Int(expectedSize) {
            throw NfcException.CommandError(
                message: "Chunked response size mismatch: expected \(expectedSize) bytes, received \(chunks.count)",
                cause: nil
            ).asError()
        }

        return chunks
    }

    public func signTransaction(
        session: NfcSession,
        psbt: Psbt,
        spendingKeyset _: SpendingKeyset
    ) async throws -> Shared.HardwareInteraction {
        // W3 requires chunked PSBT transfer
        let transferFunction = NfcSessionTransferFunction { transferSession, _, onProgress in
            // Decode base64 PSBT to binary Data for efficient transfer
            guard let psbtData = Data(base64Encoded: psbt.base64) else {
                throw NfcException.CommandError(
                    message: "Failed to decode PSBT base64",
                    cause: nil
                ).asError()
            }

            _ = try await SignStart(psbtSize: UInt32(psbtData.count))
                .transceive(session: transferSession)

            let maxChunkSize = Self.psbtChunkSize

            var offset = 0
            var sequenceId: UInt32 = 0
            var confirmationHandles: Shared.ConfirmationHandles? = nil

            while offset < psbtData.count {
                let endIndex = min(offset + maxChunkSize, psbtData.count)
                let chunk = psbtData.subdata(in: offset ..< endIndex)
                let chunkBytes = [UInt8](chunk)

                let transferResult = try await SignTransfer(
                    sequenceId: sequenceId,
                    chunkData: chunkBytes
                ).transceive(session: transferSession)

                switch transferResult {
                case .success:
                    break
                case let .confirmationPending(responseHandle, confirmationHandle):
                    confirmationHandles = Shared.ConfirmationHandles(
                        responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                        confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
                    )
                }

                offset = endIndex
                sequenceId += 1
                onProgress(Float(offset) / Float(psbtData.count))
            }

            guard let handles = confirmationHandles else {
                throw NfcException.CommandError(
                    message: "Expected ConfirmationPending from last SignTransfer but didn't receive it. W3 hardware must require user confirmation for transaction signing.",
                    cause: nil
                ).asError()
            }

            let confirmFunction = NfcSessionSuspendFunction { confirmSession, confirmCommands in
                let confirmResult = try await confirmCommands.getConfirmationResult(
                    session: confirmSession,
                    handles: handles
                )

                switch confirmResult {
                case let chunkedResult as Shared.ConfirmationResultChunkedDataAvailable:
                    let chunks = try await self.fetchAllChunks(
                        session: confirmSession,
                        commands: confirmCommands,
                        handles: handles,
                        expectedSize: chunkedResult.totalSize
                    )
                    let psbtData = Data(chunks)
                    let signedPsbtBase64 = psbtData.base64EncodedString()
                    let signedPsbt = Psbt(
                        id: psbt.id,
                        base64: signedPsbtBase64,
                        fee: psbt.fee,
                        vsize: psbt.vsize,
                        numOfInputs: psbt.numOfInputs,
                        amountSats: psbt.amountSats,
                        inputs: psbt.inputs,
                        outputs: psbt.outputs
                    )
                    return Shared.HardwareInteractionCompleted<Psbt>(result: signedPsbt) as Shared
                        .HardwareInteraction

                default:
                    throw NfcException.CommandError(
                        message: "signTransaction expected ChunkedDataAvailable result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared.HardwareInteractionRequiresConfirmation<Psbt>(
                fetchResult: confirmFunction
            ) as Shared.HardwareInteraction
        }

        return Shared.HardwareInteractionRequiresTransfer<Psbt>(
            transferAndFetch: transferFunction
        ) as Shared.HardwareInteraction
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

    public func wipeDevice(session: NfcSession) async throws -> Shared.HardwareInteraction {
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

    public func getConfirmationResult(
        session: NfcSession,
        handles: Shared.ConfirmationHandles
    ) async throws -> Shared.ConfirmationResult {
        return try await delegate.getConfirmationResult(session: session, handles: handles)
    }

    public func getConfirmationResultChunk(
        session: NfcSession,
        handles: Shared.ConfirmationHandles,
        chunkIndex: UInt32
    ) async throws -> Shared.ChunkData {
        return try await delegate.getConfirmationResultChunk(
            session: session,
            handles: handles,
            chunkIndex: chunkIndex
        )
    }

    public func getAddress(
        session: NfcSession,
        addressIndex: UInt32
    ) async throws -> String {
        // W3-only feature: generate and display address on hardware
        return try await GetAddress(addressIndex: addressIndex).transceive(session: session).address
    }

    public func verifyKeysAndBuildDescriptor(
        session: NfcSession,
        appSpendingKey: OkioByteString,
        appSpendingKeyChaincode: OkioByteString,
        networkMainnet: Bool,
        appAuthKey: OkioByteString,
        serverSpendingKey: OkioByteString,
        serverSpendingKeyChaincode: OkioByteString,
        wsmSignature: OkioByteString
    ) async throws -> KotlinBoolean {
        let result = try await VerifyKeysAndBuildDescriptor(
            appSpendingKey: appSpendingKey.toByteArray().asUInt8Array(),
            appSpendingKeyChaincode: appSpendingKeyChaincode.toByteArray().asUInt8Array(),
            networkMainnet: networkMainnet,
            appAuthKey: appAuthKey.toByteArray().asUInt8Array(),
            serverSpendingKey: serverSpendingKey.toByteArray().asUInt8Array(),
            serverSpendingKeyChaincode: serverSpendingKeyChaincode.toByteArray().asUInt8Array(),
            wsmSignature: wsmSignature.toByteArray().asUInt8Array()
        ).transceive(session: session)
        return KotlinBoolean(bool: result)
    }
}
