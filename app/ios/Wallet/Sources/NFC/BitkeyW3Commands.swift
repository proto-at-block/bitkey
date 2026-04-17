import core
import CoreNFC
import firmware
import Shared

private extension Data {
    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}

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
        fwupMode: Shared.FwupMode,
        mcuRole: Shared.McuRole,
        version: String,
        deferCommit: Bool = false
    ) async throws -> Shared.HardwareInteraction {
        return try await delegate.fwupStart(
            session: session,
            patchSize: patchSize,
            fwupMode: fwupMode,
            mcuRole: mcuRole,
            version: version,
            deferCommit: deferCommit
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

    public func getFirmwareMetadata(
        session: NfcSession,
        mcuRole: Shared.McuRole = .core
    ) async throws -> Shared.FirmwareMetadata {
        return try await delegate.getFirmwareMetadata(session: session, mcuRole: mcuRole)
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

    public func showConfirmationScreen(
        session: NfcSession,
        lockOnDismiss: Bool
    ) async throws -> KotlinBoolean {
        return try await delegate.showConfirmationScreen(
            session: session,
            lockOnDismiss: lockOnDismiss
        )
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
        throw NfcException.FeatureNotSupported().asError()
    }

    /// Maximum inputs/outputs for one-shot sign_tx_request_cmd.
    private static let maxSignTxEntries = 5
    /// NFC chunk size for streaming protocol.
    private static let streamChunkSize = 452
    /// Signatures per NFC round-trip for batched retrieval.
    /// Each TxSignatureEntry is ~112 bytes (33 pubkey + ~72 DER sig + proto tags).
    /// Must stay below MAX_PROTO_SIZE (505 bytes) including the WalletRsp wrapper.
    /// 4 × 112 + ~10 overhead ≈ 458 bytes — safely within the limit.
    private static let signatureBatchSize: UInt32 = 4

    /// Sign a transaction on W3 hardware using the non-PSBT signing protocol.
    ///
    /// For ≤5 inputs AND ≤5 outputs, uses one-shot `sign_tx_request_cmd`.
    /// For larger transactions, uses the streaming protocol which sends the canonical
    /// binary payload in 452-byte NFC chunks.
    ///
    /// Two-tap flow:
    /// 1. First tap: decomposes PSBT, sends command(s) → gets back confirmation handles
    /// 2. Second tap: calls `getConfirmationResult` → returns signatures (one-shot)
    ///    or `SignStreamReady` (streaming), then retrieves per-input signatures
    public func signTransaction(
        session: NfcSession,
        psbt: Shared.Psbt,
        spendingKeyset: SpendingKeyset,
        displayPreference: Shared.HwDisplayPreference?
    ) async throws -> Shared.HardwareInteraction {
        let decomposed: firmware.DecomposedPsbt
        do {
            decomposed = try firmware.decomposePsbt(
                psbtBase64: psbt.base64,
                originFingerprint: spendingKeyset.hardwareKey.key.origin.fingerprint
            )
        } catch {
            throw NfcException.CommandError(
                message: "Failed to decompose PSBT: \(error.localizedDescription)",
                cause: nil
            ).asError()
        }

        // Route to streaming or one-shot based on input/output count.
        let needsStreaming = decomposed.inputs.count > Self.maxSignTxEntries ||
            decomposed.outputs.count > Self.maxSignTxEntries
        if needsStreaming {
            return try signTransactionStreaming(
                psbt: psbt,
                decomposed: decomposed,
                displayPreference: displayPreference
            )
        } else {
            return try await signTransactionOneShot(
                session: session,
                psbt: psbt,
                decomposed: decomposed,
                displayPreference: displayPreference
            )
        }
    }

    /// One-shot signing for small transactions (≤5 inputs, ≤5 outputs).
    private func signTransactionOneShot(
        session: NfcSession,
        psbt: Shared.Psbt,
        decomposed: firmware.DecomposedPsbt,
        displayPreference: Shared.HwDisplayPreference?
    ) async throws -> Shared.HardwareInteraction {
        let result = try await SignTxRequest(
            version: decomposed.version,
            lockTime: decomposed.lockTime,
            inputs: decomposed.inputs,
            outputs: decomposed.outputs,
            btcDisplayUnit: displayPreference?.ffiBtcDisplayUnit ?? .satoshi
        ).transceive(session: session)

        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let signTxResult as Shared.ConfirmationResultSignTx:
                    let ffiSignatures: [firmware.InputSignatureTuple] = signTxResult.signatures
                        .map { sharedSig in
                            firmware.InputSignatureTuple(
                                inputIndex: sharedSig.inputIndex,
                                publicKey: sharedSig.publicKey.map(\.uint8Value),
                                signature: sharedSig.signature.map(\.uint8Value)
                            )
                        }
                    let signedBase64: String
                    do {
                        signedBase64 = try firmware.assemblePsbtSignatures(
                            psbtBase64: psbt.base64,
                            signatures: ffiSignatures
                        )
                    } catch {
                        throw NfcException.CommandError(
                            message: "Failed to assemble PSBT signatures: \(error.localizedDescription)",
                            cause: nil
                        ).asError()
                    }
                    let signedPsbt = Shared.Psbt(
                        id: psbt.id,
                        base64: signedBase64,
                        fee: psbt.fee,
                        vsize: psbt.vsize,
                        numOfInputs: psbt.numOfInputs,
                        amountSats: psbt.amountSats,
                        inputs: psbt.inputs,
                        outputs: psbt.outputs
                    )
                    return Shared.HardwareInteractionCompleted<Shared.Psbt>(
                        result: signedPsbt
                    ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "signTransaction expected SignTx result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared.HardwareInteractionRequiresConfirmation<Shared.Psbt>(
                handles: handles,
                mapResult: mapper
            ) as Shared.HardwareInteraction
        }
    }

    /// Streaming signing for large transactions (>5 inputs or >5 outputs).
    ///
    /// Returns RequiresTransfer so the state machine's progress pipeline captures
    /// chunk upload progress (same visual pattern as FWUP).
    ///
    /// Flow:
    /// 1. [First tap] RequiresTransfer callback streams the payload with progress:
    ///    sign_stream_start → sign_stream_transfer × N → sign_stream_finalize
    ///    → returns RequiresConfirmation
    /// 2. [User confirms on device]
    /// 3. [Second tap] RequiresTransfer callback retrieves signatures with progress:
    ///    get_tx_signature × num_inputs → returns Completed
    private func signTransactionStreaming(
        psbt: Shared.Psbt,
        decomposed: firmware.DecomposedPsbt,
        displayPreference: Shared.HwDisplayPreference?
    ) throws -> Shared.HardwareInteraction {
        // Serialize canonical payload eagerly so errors surface immediately
        let streamPayload: firmware.StreamPayload
        do {
            streamPayload = try firmware.serializeSignStreamPayload(
                version: decomposed.version,
                lockTime: decomposed.lockTime,
                inputs: decomposed.inputs,
                outputs: decomposed.outputs
            )
        } catch {
            throw NfcException.CommandError(
                message: "Failed to serialize stream payload: \(error.localizedDescription)",
                cause: nil
            ).asError()
        }

        // Return RequiresTransfer — the state machine will call transferAndFetch
        // with an onProgress callback to drive the progress bar UI.
        let transferFn = NfcSessionTransferFunction { [self] transferSession, _, onProgress in
            return try await self.streamPayloadAndFinalize(
                session: transferSession,
                psbt: psbt,
                decomposed: decomposed,
                streamPayload: streamPayload,
                displayPreference: displayPreference,
                onProgress: onProgress
            )
        }
        return Shared.HardwareInteractionRequiresTransfer<Shared.Psbt>(
            transferAndFetch: transferFn
        ) as Shared.HardwareInteraction
    }

    /// Streams the canonical binary payload to the device and finalizes the session.
    /// Called inside a RequiresTransfer callback with progress reporting.
    private func streamPayloadAndFinalize(
        session: NfcSession,
        psbt: Shared.Psbt,
        decomposed: firmware.DecomposedPsbt,
        streamPayload: firmware.StreamPayload,
        displayPreference: Shared.HwDisplayPreference?,
        onProgress: Shared.NfcProgressCallback
    ) async throws -> Shared.HardwareInteraction {
        // Step 1: Start streaming session
        let startResult = try await SignStreamStart(
            numInputs: UInt32(decomposed.inputs.count),
            numOutputs: UInt32(decomposed.outputs.count),
            version: decomposed.version,
            lockTime: decomposed.lockTime,
            payloadSize: streamPayload.payloadSize,
            btcDisplayUnit: displayPreference?.ffiBtcDisplayUnit ?? .satoshi
        ).transceive(session: session)
        guard case .success = startResult else {
            throw NfcException.CommandError(
                message: "sign_stream_start failed",
                cause: nil
            ).asError()
        }

        // Step 2: Stream payload in 452-byte chunks with progress reporting
        let payloadData = streamPayload.data
        let totalChunks = (payloadData.count + Self.streamChunkSize - 1) / Self.streamChunkSize
        for chunkIndex in 0 ..< totalChunks {
            let offset = chunkIndex * Self.streamChunkSize
            let end = min(offset + Self.streamChunkSize, payloadData.count)
            let chunkData = Array(payloadData[offset ..< end])

            let transferResult = try await SignStreamTransfer(
                sequenceId: UInt32(chunkIndex),
                chunkData: chunkData
            ).transceive(session: session)
            guard case .success = transferResult else {
                throw NfcException.CommandError(
                    message: "sign_stream_transfer failed at chunk \(chunkIndex)",
                    cause: nil
                ).asError()
            }

            // Report chunk upload progress (0.0 → 1.0)
            onProgress.onProgress(progress: Float(chunkIndex + 1) / Float(totalChunks))
        }

        // Step 3: Finalize with commitment hash
        let finalizeResult = try await SignStreamFinalize(
            commitmentHash: streamPayload.commitmentHash
        ).transceive(session: session)

        switch finalizeResult {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let streamReady as Shared.ConfirmationResultSignStreamReady:
                    // Return RequiresTransfer for per-input signature retrieval
                    // with progress reporting on the second tap.
                    let sigTransferFn = NfcSessionTransferFunction { sigSession, _, sigProgress in
                        let batchSize: UInt32 = Self.signatureBatchSize
                        var ffiSignatures: [firmware.InputSignatureTuple] = []
                        var startIndex: UInt32 = 0
                        while startIndex < streamReady.numInputs {
                            let count = min(batchSize, streamReady.numInputs - startIndex)
                            let batchSigs = try await GetTxSignaturesBatch(
                                startIndex: startIndex,
                                count: count
                            ).transceive(session: sigSession)
                            for (offset, txSig) in batchSigs.enumerated() {
                                ffiSignatures.append(firmware.InputSignatureTuple(
                                    inputIndex: startIndex + UInt32(offset),
                                    publicKey: txSig.pubkey,
                                    signature: txSig.signature
                                ))
                            }
                            startIndex += UInt32(batchSigs.count)
                            // Report progress per batch (0.0 → 1.0)
                            sigProgress
                                .onProgress(
                                    progress: Float(startIndex) /
                                        Float(streamReady.numInputs)
                                )
                        }
                        let signedBase64: String
                        do {
                            signedBase64 = try firmware.assemblePsbtSignatures(
                                psbtBase64: psbt.base64,
                                signatures: ffiSignatures
                            )
                        } catch {
                            throw NfcException.CommandError(
                                message: "Failed to assemble streaming PSBT signatures: \(error.localizedDescription)",
                                cause: nil
                            ).asError()
                        }
                        let signedPsbt = Shared.Psbt(
                            id: psbt.id,
                            base64: signedBase64,
                            fee: psbt.fee,
                            vsize: psbt.vsize,
                            numOfInputs: psbt.numOfInputs,
                            amountSats: psbt.amountSats,
                            inputs: psbt.inputs,
                            outputs: psbt.outputs
                        )
                        return Shared.HardwareInteractionCompleted<Shared.Psbt>(
                            result: signedPsbt
                        ) as Shared.HardwareInteraction
                    }
                    return Shared.HardwareInteractionRequiresTransfer<Shared.Psbt>(
                        transferAndFetch: sigTransferFn
                    ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "signTransaction (streaming) expected SignStreamReady but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared.HardwareInteractionRequiresConfirmation<Shared.Psbt>(
                handles: handles,
                mapResult: mapper
            ) as Shared.HardwareInteraction
        }
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

    public func signActionProof(
        session: NfcSession,
        version: UInt32,
        action: Shared.ActionProofAction,
        value: String?,
        bindings: String
    ) async throws -> Shared.HardwareInteraction {
        let result = try await SignActionProof(
            version: version,
            action: action.toPascalCase(),
            value: value,
            bindings: bindings
        ).transceive(session: session)
        switch result {
        case let .success(signature):
            let hexSignature = signature.map { String(format: "%02x", $0) }.joined()
            return Shared.HardwareInteractionCompleted<NSString>(
                result: hexSignature as NSString
            ) as Shared.HardwareInteraction
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let signActionProofResult as Shared.ConfirmationResultSignActionProof:
                    return Shared.HardwareInteractionCompleted<NSString>(
                        result: signActionProofResult.signature as NSString
                    ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "signActionProof expected SignActionProof result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared.HardwareInteractionRequiresConfirmation<NSString>(
                handles: handles,
                mapResult: mapper
            ) as Shared.HardwareInteraction
        }
    }

    /// Signs a challenge with user confirmation during lost app recovery (W3 only).
    public func lostAppRecoverySignChallenge(
        session: NfcSession,
        challenge: OkioByteString
    ) async throws -> Shared.HardwareInteraction {
        let result = try await LostAppRecoverySignChallenge(
            challenge: challenge.toByteArray().asUInt8Array()
        ).transceive(session: session)

        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let signChallengeResult as Shared
                    .ConfirmationResultLostAppRecoverySignChallenge:
                    return Shared.HardwareInteractionCompleted<NSString>(
                        result: signChallengeResult.signature as NSString
                    ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "lostAppRecoverySignChallenge expected LostAppRecoverySignChallenge result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared.HardwareInteractionRequiresConfirmation<NSString>(
                handles: handles,
                mapResult: mapper
            ) as Shared.HardwareInteraction
        }
    }

    public func lostAppRecovery(
        session: NfcSession,
        sealedSsek: OkioByteString,
        onSsekUnsealed: any Shared.KotlinSuspendFunction1
    ) async throws -> Shared.HardwareInteraction {
        // Step 1: Send LostAppRecovery command with sealed SSEK
        let result = try await LostAppRecovery(
            sealedSsek: sealedSsek.toByteArray().asUInt8Array()
        ).transceive(session: session)

        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { [onSsekUnsealed] confirmResult in
                switch confirmResult {
                case let ssekResult as Shared.ConfirmationResultLostAppRecoverySsek:
                    // Got unsealed SSEK — return RequiresTransfer to run
                    // the async callback + continue command in the same NFC session.
                    let ssekBytes = ssekResult.ssek.map(\.uint8Value)
                    let transferFn = NfcSessionTransferFunction {
                        transferSession, _, _ in
                        // Build SymmetricKey from unsealed SSEK bytes
                        let ssekByteString = OkioKt.ByteString(data: Data(ssekBytes))
                        let ssekKey = Shared.SymmetricKeyImpl(raw: ssekByteString)

                        // Call Kotlin callback to decrypt descriptors and build params
                        guard let params = try await onSsekUnsealed
                            .invoke(p1: ssekKey) as? Shared.LostAppRecoveryContinueParams
                        else {
                            throw NfcException.CommandError(
                                message: "onSsekUnsealed returned unexpected type",
                                cause: nil
                            ).asError()
                        }

                        // Build and transceive the continue command
                        // PublicKey<T> is a Kotlin inline value class with a generic
                        // type parameter, so ObjC export erases it to Any.
                        // Cast to String to access the underlying hex value.
                        let authKeyHex = params.appGlobalAuthKey as! String
                        let authKeyBytes = OkioByteString.companion
                            .decodeHex(authKeyHex)
                            .toByteArray()
                            .asUInt8Array()
                        let continueResult = try await LostAppRecoveryContinue(
                            actionProofVersion: params.actionProofVersion,
                            action: params.actionProofAction.toPascalCase(),
                            value: nil,
                            bindings: params.actionProofBindings,
                            existingDescriptorPublicKeys: params.existingHwSpendingKeys
                                .map(\.key.dpub),
                            network: params.network.btcNetwork,
                            appGlobalAuthKey: authKeyBytes
                        ).transceive(session: transferSession)

                        let compositeResult = Shared.LostAppRecoveryCompositeResult(
                            actionProofSignature: continueResult.actionProofSignature
                                .map { String(format: "%02x", $0) }.joined(),
                            spendingKeyDpub: Shared.DescriptorPublicKey.companion.invoke(
                                dpub: continueResult.spendingKeyDpub
                            ),
                            appAuthKeySignature: continueResult.appAuthKeySignature
                                .map { String(format: "%02x", $0) }.joined()
                        )
                        return Shared
                            .HardwareInteractionCompleted<Shared.LostAppRecoveryCompositeResult>(
                                result: compositeResult
                            ) as Shared.HardwareInteraction
                    }
                    return Shared
                        .HardwareInteractionRequiresTransfer<
                            Shared
                                .LostAppRecoveryCompositeResult
                        >(
                            transferAndFetch: transferFn
                        ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "lostAppRecovery expected LostAppRecoverySsek result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared
                .HardwareInteractionRequiresConfirmation<Shared.LostAppRecoveryCompositeResult>(
                    handles: handles,
                    mapResult: mapper
                ) as Shared.HardwareInteraction
        }
    }

    public func signChallengeAndSealSeks(
        session: NfcSession,
        challenge: OkioByteString,
        unsealedCsek: OkioByteString,
        unsealedSsek: OkioByteString
    ) async throws -> Shared.HardwareInteraction {
        let result = try await SignChallengeAndSealSeks(
            challenge: challenge.toByteArray().asUInt8Array(),
            unsealedCsek: unsealedCsek.toByteArray().asUInt8Array(),
            unsealedSsek: unsealedSsek.toByteArray().asUInt8Array()
        ).transceive(session: session)

        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let scsResult as Shared.ConfirmationResultSignChallengeAndSealSeks:
                    let result = Shared.SignChallengeAndSealSeksResult(
                        signedChallenge: scsResult.signature.map { String(
                            format: "%02x",
                            $0.uint8Value
                        ) }.joined(),
                        sealedCsek: OkioKt
                            .ByteString(data: Data(scsResult.sealedCsek.map(\.uint8Value))),
                        sealedSsek: OkioKt
                            .ByteString(data: Data(scsResult.sealedSsek.map(\.uint8Value)))
                    )
                    return Shared
                        .HardwareInteractionCompleted<Shared.SignChallengeAndSealSeksResult>(
                            result: result
                        ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "signChallengeAndSealSeks expected SignChallengeAndSealSeks result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared
                .HardwareInteractionRequiresConfirmation<Shared.SignChallengeAndSealSeksResult>(
                    handles: handles,
                    mapResult: mapper
                ) as Shared.HardwareInteraction
        }
    }

    public func recoveryAuthorizeLostApp(
        session: NfcSession,
        sealedDdkData: OkioByteString?,
        sealedSsekForDecryption: OkioByteString?,
        descriptorBackupsBindings: String,
        activateKeysetBindings: String,
        actionProofVersion: UInt32
    ) async throws -> Shared.HardwareInteraction {
        let result = try await RecoveryAuthorizeLostApp(
            sealedDdk: sealedDdkData?.toByteArray().asUInt8Array() ?? [],
            sealedSsek: sealedSsekForDecryption?.toByteArray().asUInt8Array() ?? [],
            descriptorBackupsBindings: descriptorBackupsBindings,
            activateKeysetBindings: activateKeysetBindings,
            actionProofVersion: actionProofVersion
        ).transceive(session: session)

        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let rlaResult as Shared.ConfirmationResultRecoveryAuthorizeLostApp:
                    let result = Shared.RecoveryAuthorizeLostAppResult(
                        descriptorBackupsSignature: rlaResult.descriptorBackupsSignature
                            .map { String(
                                format: "%02x",
                                $0.uint8Value
                            ) }.joined(),
                        activateKeysetSignature: rlaResult.activateKeysetSignature.map { String(
                            format: "%02x",
                            $0.uint8Value
                        ) }.joined(),
                        unsealedDdkData: rlaResult.unsealedDdkData.isEmpty ? nil : OkioKt
                            .ByteString(data: Data(rlaResult.unsealedDdkData.map(\.uint8Value))),
                        unsealedSsek: rlaResult.unsealedSsek.isEmpty ? nil : OkioKt
                            .ByteString(data: Data(rlaResult.unsealedSsek.map(\.uint8Value)))
                    )
                    return Shared
                        .HardwareInteractionCompleted<Shared.RecoveryAuthorizeLostAppResult>(
                            result: result
                        ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "recoveryAuthorizeLostApp expected RecoveryAuthorizeLostApp result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared
                .HardwareInteractionRequiresConfirmation<Shared.RecoveryAuthorizeLostAppResult>(
                    handles: handles,
                    mapResult: mapper
                ) as Shared.HardwareInteraction
        }
    }

    public func recoveryAuthorizeLostHw(
        session: NfcSession,
        ddkPrivateKeyBytes: OkioByteString?,
        descriptorBackupsBindings: String,
        activateKeysetBindings: String,
        actionProofVersion: UInt32
    ) async throws -> Shared.HardwareInteraction {
        let result = try await RecoveryAuthorizeLostHw(
            ddkPrivateKey: ddkPrivateKeyBytes?.toByteArray().asUInt8Array() ?? [],
            descriptorBackupsBindings: descriptorBackupsBindings,
            activateKeysetBindings: activateKeysetBindings,
            actionProofVersion: actionProofVersion
        ).transceive(session: session)

        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let rlhResult as Shared.ConfirmationResultRecoveryAuthorizeLostHw:
                    let result = Shared.RecoveryAuthorizeLostHwResult(
                        descriptorBackupsSignature: rlhResult.descriptorBackupsSignature
                            .map { String(
                                format: "%02x",
                                $0.uint8Value
                            ) }.joined(),
                        activateKeysetSignature: rlhResult.activateKeysetSignature.map { String(
                            format: "%02x",
                            $0.uint8Value
                        ) }.joined(),
                        sealedDdkData: rlhResult.sealedDdkData.isEmpty ? nil : OkioKt
                            .ByteString(data: Data(rlhResult.sealedDdkData.map(\.uint8Value)))
                    )
                    return Shared
                        .HardwareInteractionCompleted<Shared.RecoveryAuthorizeLostHwResult>(
                            result: result
                        ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "recoveryAuthorizeLostHw expected RecoveryAuthorizeLostHw result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared
                .HardwareInteractionRequiresConfirmation<Shared.RecoveryAuthorizeLostHwResult>(
                    handles: handles,
                    mapResult: mapper
                ) as Shared.HardwareInteraction
        }
    }

    public func upgradeAuthorizeW3(
        session: NfcSession,
        ddkPrivateKeyBytes: OkioByteString,
        descriptorBackupsBindings: String,
        activateKeysetBindings: String,
        actionProofVersion: UInt32
    ) async throws -> Shared.HardwareInteraction {
        let result = try await UpgradeAuthorizeW3(
            ddkPrivateKey: ddkPrivateKeyBytes.toByteArray().asUInt8Array(),
            descriptorBackupsBindings: descriptorBackupsBindings,
            activateKeysetBindings: activateKeysetBindings,
            actionProofVersion: actionProofVersion
        ).transceive(session: session)

        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let uaw3Result as Shared.ConfirmationResultUpgradeAuthorizeW3:
                    let result = Shared.UpgradeAuthorizeW3Result(
                        descriptorBackupsSignature: uaw3Result.descriptorBackupsSignature
                            .map { String(
                                format: "%02x",
                                $0.uint8Value
                            ) }.joined(),
                        activateKeysetSignature: uaw3Result.activateKeysetSignature.map { String(
                            format: "%02x",
                            $0.uint8Value
                        ) }.joined(),
                        sealedDdkData: OkioKt
                            .ByteString(data: Data(uaw3Result.sealedDdkData.map(\.uint8Value)))
                    )
                    return Shared
                        .HardwareInteractionCompleted<Shared.UpgradeAuthorizeW3Result>(
                            result: result
                        ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "upgradeAuthorizeW3 expected UpgradeAuthorizeW3 result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared
                .HardwareInteractionRequiresConfirmation<Shared.UpgradeAuthorizeW3Result>(
                    handles: handles,
                    mapResult: mapper
                ) as Shared.HardwareInteraction
        }
    }

    public func rotateAppAuthKeys(
        session: NfcSession,
        params: Shared.RotateAppAuthKeysContinueParams
    ) async throws -> Shared.HardwareInteraction {
        let result = try await RotateAppAuthKeys(
            actionProofVersion: params.actionProofVersion,
            action: params.actionProofAction.toPascalCase(),
            value: nil,
            bindings: params.actionProofBindings,
            accountId: params.accountId,
            appGlobalAuthKey: params.appGlobalAuthPublicKey
        ).transceive(session: session)
        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let raakResult as Shared.ConfirmationResultRotateAppAuthKeys:
                    let actionProofSig = raakResult.actionProofSignature.map(\.uint8Value)
                    let hwSignedAcctId = raakResult.hwSignedAccountId.map(\.uint8Value)
                    let appAuthKeySig = raakResult.appAuthKeySignature.map(\.uint8Value)
                    let hwAuthPubKey = raakResult.hwAuthPublicKey.map(\.uint8Value)
                    let hwSignedAcctIdDer = try core
                        .compactSignatureToDer(compactSignature: hwSignedAcctId)
                    let appAuthKeySigDer = try core
                        .compactSignatureToDer(compactSignature: appAuthKeySig)
                    let compositeResult = Shared.RotateAppAuthKeysCompositeResult(
                        actionProofSignature: Data(actionProofSig).hexString,
                        hwSignedAccountId: Data(hwSignedAcctIdDer).hexString,
                        appGlobalAuthKeyHwSignature: Data(appAuthKeySigDer).hexString,
                        hwAuthPublicKey: Shared.HwAuthPublicKey(
                            pubKey: Shared.Secp256k1PublicKey(value: Data(hwAuthPubKey).hexString)
                        )
                    )
                    return Shared
                        .HardwareInteractionCompleted<Shared.RotateAppAuthKeysCompositeResult>(
                            result: compositeResult
                        ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "rotateAppAuthKeys expected RotateAppAuthKeys result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared
                .HardwareInteractionRequiresConfirmation<Shared.RotateAppAuthKeysCompositeResult>(
                    handles: handles,
                    mapResult: mapper
                ) as Shared.HardwareInteraction
        }
    }

    /// Upgrade rotate app auth keys (W3 upgrade flow, no action proof signing).
    public func upgradeRotateAppAuthKeys(
        session: NfcSession,
        params: Shared.UpgradeRotateAppAuthKeysParams
    ) async throws -> Shared.HardwareInteraction {
        let result = try await UpgradeRotateAppAuthKeys(
            accountId: params.accountId,
            appGlobalAuthKey: params.appGlobalAuthPublicKey
        ).transceive(session: session)
        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let uraakResult as Shared.ConfirmationResultUpgradeRotateAppAuthKeys:
                    let hwSignedAcctId = uraakResult.hwSignedAccountId.map(\.uint8Value)
                    let appAuthKeySig = uraakResult.appAuthKeySignature.map(\.uint8Value)
                    let hwAuthPubKey = uraakResult.hwAuthPublicKey.map(\.uint8Value)
                    let hwSignedAcctIdDer = try core
                        .compactSignatureToDer(compactSignature: hwSignedAcctId)
                    let appAuthKeySigDer = try core
                        .compactSignatureToDer(compactSignature: appAuthKeySig)
                    let compositeResult = Shared.UpgradeRotateAppAuthKeysResult(
                        hwSignedAccountId: Data(hwSignedAcctIdDer).hexString,
                        appGlobalAuthKeyHwSignature: Data(appAuthKeySigDer).hexString,
                        hwAuthPublicKey: Shared.HwAuthPublicKey(
                            pubKey: Shared.Secp256k1PublicKey(value: Data(hwAuthPubKey).hexString)
                        )
                    )
                    return Shared
                        .HardwareInteractionCompleted<Shared.UpgradeRotateAppAuthKeysResult>(
                            result: compositeResult
                        ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "upgradeRotateAppAuthKeys expected UpgradeRotateAppAuthKeys result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared
                .HardwareInteractionRequiresConfirmation<Shared.UpgradeRotateAppAuthKeysResult>(
                    handles: handles,
                    mapResult: mapper
                ) as Shared.HardwareInteraction
        }
    }

    /// EEK restoration unseal symmetric key on W3 hardware with user confirmation.
    ///
    /// First tap: sends sealed key → firmware shows confirmation prompt → CONFIRMATION_PENDING.
    /// Second tap: getConfirmationResult returns the unsealed symmetric key.
    public func eekRestorationUnsealSymmetricKey(
        session: NfcSession,
        sealedKey: OkioByteString
    ) async throws -> Shared.HardwareInteraction {
        let result = try await EekRestorationUnseal(
            sealedKey: sealedKey.toByteArray().asUInt8Array()
        ).transceive(session: session)

        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let mapper = NfcConfirmationResultMapper { confirmResult in
                switch confirmResult {
                case let eekResult as Shared.ConfirmationResultEekRestorationUnsealSymmetricKey:
                    let unsealedKeyBytes = eekResult.unsealedKey.map(\.uint8Value)
                    let keyByteString = OkioKt.ByteString(data: Data(unsealedKeyBytes))
                    let symmetricKey = Shared.SymmetricKeyImpl(raw: keyByteString)
                    return Shared
                        .HardwareInteractionCompleted<Shared.SymmetricKeyImpl>(
                            result: symmetricKey
                        ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "eekRestorationUnsealSymmetricKey expected EekRestorationUnsealSymmetricKey result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared
                .HardwareInteractionRequiresConfirmation<Shared.SymmetricKeyImpl>(
                    handles: handles,
                    mapResult: mapper
                ) as Shared.HardwareInteraction
        }
    }

    /// Full account cloud backup restoration on W3 hardware with user confirmation.
    ///
    /// First tap: firmware shows confirmation prompt → CONFIRMATION_PENDING.
    /// Second tap: streams sealed CSEKs to firmware one at a time. When firmware successfully
    /// unseals one, calls onCsekUnsealed with the result.
    public func fullAccountCloudBackupRestoration(
        session: NfcSession,
        sealedCseks: [OkioByteString],
        onCsekUnsealed: any Shared.KotlinSuspendFunction1
    ) async throws -> Shared.HardwareInteraction {
        let result = try await FullAccountCloudBackupRestoration()
            .transceive(session: session)

        switch result {
        case let .confirmationPending(responseHandle, confirmationHandle):
            let handles = Shared.ConfirmationHandles(
                responseHandle: responseHandle.map { KotlinUByte(value: $0) },
                confirmationHandle: confirmationHandle.map { KotlinUByte(value: $0) }
            )
            let sessionToken = responseHandle
            let mapper = NfcConfirmationResultMapper { [
                sealedCseks,
                onCsekUnsealed,
                sessionToken
            ] confirmResult in
                switch confirmResult {
                case is Shared.ConfirmationResultFullAccountCloudBackupRestoration:
                    // Session confirmed — return RequiresTransfer to stream CSEKs
                    let transferFn = NfcSessionTransferFunction {
                        transferSession, _, _ in
                        let unsealResult = try await self.streamCseksToFirmwareIOS(
                            session: transferSession,
                            sealedCseks: sealedCseks,
                            sessionToken: sessionToken
                        )
                        let callbackResult = try await onCsekUnsealed.invoke(p1: unsealResult)!
                        return Shared
                            .HardwareInteractionCompleted<AnyObject>(
                                result: callbackResult as AnyObject
                            ) as Shared.HardwareInteraction
                    }
                    return Shared
                        .HardwareInteractionRequiresTransfer<AnyObject>(
                            transferAndFetch: transferFn
                        ) as Shared.HardwareInteraction
                case is Shared.ConfirmationResultPending:
                    throw NfcException.ConfirmationPending().asError()
                case is Shared.ConfirmationResultDenied:
                    throw NfcException.UserDenied().asError()
                default:
                    throw NfcException.CommandError(
                        message: "fullAccountCloudBackupRestoration expected FullAccountCloudBackupRestoration result but got: \(type(of: confirmResult))",
                        cause: nil
                    ).asError()
                }
            }
            return Shared
                .HardwareInteractionRequiresConfirmation<AnyObject>(
                    handles: handles,
                    mapResult: mapper
                ) as Shared.HardwareInteraction
        }
    }

    /// Streams sealed CSEKs to firmware one at a time within a confirmed restoration session.
    /// Each CSEK is sent with its index. On the first success, firmware returns the unsealed
    /// key and echoes the index back.
    private func streamCseksToFirmwareIOS(
        session: NfcSession,
        sealedCseks: [OkioByteString],
        sessionToken: [UInt8]
    ) async throws -> Shared.CsekUnsealResult {
        var lastError: Error?
        for (index, sealedCsek) in sealedCseks.enumerated() {
            do {
                return try await unsealCsekInRestorationSessionIOS(
                    session: session,
                    index: UInt32(index),
                    sealedCsek: sealedCsek,
                    sessionToken: sessionToken
                )
            } catch let error where Self.isCsekUnsealRetryable(error) {
                lastError = error
                // Firmware couldn't unseal this CSEK, try the next one
            }
            // All other errors (transport, auth, session) propagate immediately
        }
        throw lastError ?? NfcException.CommandError(
            message: "Could not unseal any CSEK with this hardware",
            cause: nil
        ).asError()
    }

    /// Sends a single sealed CSEK with its index to firmware for unsealing.
    private func unsealCsekInRestorationSessionIOS(
        session: NfcSession,
        index: UInt32,
        sealedCsek: OkioByteString,
        sessionToken: [UInt8]
    ) async throws -> Shared.CsekUnsealResult {
        let result = try await FullAccountCloudBackupRestorationContinue(
            sealedCsek: sealedCsek.toByteArray().asUInt8Array(),
            csekIndex: index,
            sessionToken: sessionToken
        ).transceive(session: session)

        let unsealedKeyBytes = result.unsealedCsek
        let keyByteString = OkioKt.ByteString(data: Data(unsealedKeyBytes))
        let symmetricKey = Shared.SymmetricKeyImpl(raw: keyByteString)
        return Shared.CsekUnsealResult(
            index: Int32(result.csekIndex),
            unsealedCsek: symmetricKey
        )
    }

    /// Returns true if the error is a CSEK unseal failure that should be retried with the next
    /// CSEK.
    /// Only command-level errors (unseal mismatch, generic command error) are retryable.
    /// Transport, auth, and session errors return false so they propagate immediately.
    private static func isCsekUnsealRetryable(_ error: Error) -> Bool {
        let nsError = error as NSError
        // NfcException.CommandErrorSealCsekResponseUnsealException — explicit unseal mismatch
        // NfcException.CommandError — generic firmware command error during unseal
        // Matches Android's catch(CommandErrorSealCsekResponseUnsealException) +
        // catch(CommandError)
        return nsError.isKotlinNfcCsekUnsealError() || nsError
            .isKotlinNfcCommandError(containing: "")
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
        wsmSignature: OkioByteString,
        accountIndex: UInt32
    ) async throws -> String {
        return try await VerifyKeysAndBuildDescriptor(
            appSpendingKey: appSpendingKey.toByteArray().asUInt8Array(),
            appSpendingKeyChaincode: appSpendingKeyChaincode.toByteArray().asUInt8Array(),
            networkMainnet: networkMainnet,
            appAuthKey: appAuthKey.toByteArray().asUInt8Array(),
            serverSpendingKey: serverSpendingKey.toByteArray().asUInt8Array(),
            serverSpendingKeyChaincode: serverSpendingKeyChaincode.toByteArray().asUInt8Array(),
            wsmSignature: wsmSignature.toByteArray().asUInt8Array(),
            accountIndex: accountIndex
        ).transceive(session: session)
    }
}

// MARK: - Display Preference mapping

private extension Shared.HwDisplayPreference {
    /// Maps the Kotlin display preference's bitcoin unit to the Rust FFI enum.
    var ffiBtcDisplayUnit: firmware.BtcDisplayUnit {
        switch bitcoinDisplayUnit {
        case .satoshi: return .satoshi
        case .bitcoin: return .bitcoin
        default: return .satoshi
        }
    }
}

// MARK: - Action Proof PascalCase conversions

private extension Shared.ActionProofAction {
    /// Converts the enum to its canonical PascalCase string for the firmware protocol.
    func toPascalCase() -> String {
        switch self {
        case .setSpendWithoutHardware: return "SetSpendWithoutHardware"
        case .disableSpendWithoutHardware: return "DisableSpendWithoutHardware"
        case .setVerificationThreshold: return "SetVerificationThreshold"
        case .setRecoveryEmail: return "SetRecoveryEmail"
        case .disableRecoveryEmail: return "DisableRecoveryEmail"
        case .setRecoveryPhone: return "SetRecoveryPhone"
        case .disableRecoveryPhone: return "DisableRecoveryPhone"
        case .setRecoveryPushNotifications: return "SetRecoveryPushNotifications"
        case .disableRecoveryPushNotifications: return "DisableRecoveryPushNotifications"
        case .addRecoveryContact: return "AddRecoveryContact"
        case .removeRecoveryContact: return "RemoveRecoveryContact"
        case .removeRecoveryCustomer: return "RemoveRecoveryCustomer"
        case .addBeneficiary: return "AddBeneficiary"
        case .removeBeneficiary: return "RemoveBeneficiary"
        case .removeBenefactor: return "RemoveBenefactor"
        case .createSpendingKeyset: return "CreateSpendingKeyset"
        case .rotateSpendingKeyset: return "RotateSpendingKeyset"
        case .deleteAccount: return "DeleteAccount"
        case .updateDescriptorBackups: return "UpdateDescriptorBackups"
        case .createLostAppRecovery: return "CreateLostAppRecovery"
        case .createLostHardwareRecovery: return "CreateLostHardwareRecovery"
        case .cancelLostAppRecovery: return "CancelLostAppRecovery"
        case .cancelLostHardwareRecovery: return "CancelLostHardwareRecovery"
        case .cancelConflictingRecovery: return "CancelConflictingRecovery"
        case .sendRecoveryVerificationCode: return "SendRecoveryVerificationCode"
        case .verifyRecoveryVerificationCode: return "VerifyRecoveryVerificationCode"
        case .rotateAppAuthKeys: return "RotateAppAuthKeys"
        default: fatalError("Unknown ActionProofAction: \(self.name)")
        }
    }
}
