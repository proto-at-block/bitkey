package build.wallet.nfc

import bitkey.data.PrivateData
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.SealedData
import build.wallet.crypto.SymmetricKey
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.encrypt.SignatureUtils
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.nfc.platform.*
import build.wallet.nfc.platform.ConfirmationHandles
import build.wallet.nfc.platform.ConfirmationResult
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.W3NfcCommands
import build.wallet.rust.firmware.*
import build.wallet.toByteString
import build.wallet.toUByteList
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import build.wallet.rust.firmware.BtcDisplayUnit as FfiBtcDisplayUnit
import build.wallet.rust.firmware.BtcNetwork as FfiBtcNetwork
import build.wallet.rust.firmware.InputSignatureTuple as FfiInputSignatureTuple
import build.wallet.rust.firmware.RecoveryAuthorizeLostAppResult as FfiRecoveryAuthorizeLostAppResult
import build.wallet.rust.firmware.RecoveryAuthorizeLostHwResult as FfiRecoveryAuthorizeLostHwResult
import build.wallet.rust.firmware.SweepXpub as FfiSweepXpub
import build.wallet.rust.firmware.RotateAppAuthKeys as FfiRotateAppAuthKeys
import build.wallet.rust.firmware.RotateAppAuthKeysResult as FfiRotateAppAuthKeysResult
import build.wallet.rust.firmware.RotateAppAuthKeysResultState as FfiRotateAppAuthKeysResultState
import build.wallet.rust.firmware.SignChallengeAndSealSeksResult as FfiSignChallengeAndSealSeksResult
import build.wallet.rust.firmware.UpgradeAuthorizeW3 as FfiUpgradeAuthorizeW3
import build.wallet.rust.firmware.UpgradeAuthorizeW3Result as FfiUpgradeAuthorizeW3Result
import build.wallet.rust.firmware.UpgradeAuthorizeW3ResultState as FfiUpgradeAuthorizeW3ResultState
import build.wallet.rust.firmware.UpgradeRotateAppAuthKeys as FfiUpgradeRotateAppAuthKeys
import build.wallet.rust.firmware.UpgradeRotateAppAuthKeysResult as FfiUpgradeRotateAppAuthKeysResult
import build.wallet.rust.firmware.UpgradeRotateAppAuthKeysResultState as FfiUpgradeRotateAppAuthKeysResultState

/**
 * W3-specific NFC commands that delegate to the base implementation.
 *
 * Overrides W3-only features like [getAddress] which are not supported on W1 hardware.
 */
@Suppress("LargeClass")
class BitkeyW3Commands(
  private val delegate: NfcCommands,
  private val signatureUtils: SignatureUtils,
) : W3NfcCommands, NfcCommands by delegate {
  /**
   * Generate and display a bitcoin address on the W3 hardware device.
   *
   * This is a W3-only feature - the hardware derives the address from its stored descriptor
   * at the given index and displays it on screen for user verification.
   *
   * @param session the active NfcSession
   * @param addressIndex the address index for derivation (0, 1, 2, etc.)
   * @return the derived address string
   */
  override suspend fun getAddress(
    session: NfcSession,
    addressIndex: UInt,
  ): String =
    executeCommand(
      session = session,
      generateCommand = { GetAddress(addressIndex) },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: GetAddressResultState.Data -> state.response },
      generateResult = { state: GetAddressResultState.Result -> state.value.address }
    )

  /**
   * Verifies app spending key, app auth key, and server spending key on W3 hardware,
   * and builds the wallet descriptor.
   *
   * This is a W3-only feature for verifying the keyset required for wallet operation.
   *
   * @param session the active [NfcSession]
   * @param appSpendingKey 33-byte compressed secp256k1 public key for app spending
   * @param appSpendingKeyChaincode 32-byte chaincode for app spending key
   * @param networkMainnet true for mainnet, false for testnet
   * @param appAuthKey 33-byte compressed secp256k1 public key for app authentication
   * @param serverSpendingKey 33-byte compressed secp256k1 public key for server spending
   * @param serverSpendingKeyChaincode 32-byte chaincode for server spending key
   * @param wsmSignature 64-byte compact ECDSA signature from WSM
   */
  override suspend fun verifyKeysAndBuildDescriptor(
    session: NfcSession,
    appSpendingKey: ByteString,
    appSpendingKeyChaincode: ByteString,
    networkMainnet: Boolean,
    appAuthKey: ByteString,
    serverSpendingKey: ByteString,
    serverSpendingKeyChaincode: ByteString,
    wsmSignature: ByteString,
    accountIndex: UInt,
  ): String =
    executeCommand(
      session = session,
      generateCommand = {
        VerifyKeysAndBuildDescriptor(
          appSpendingKey.toUByteList(),
          appSpendingKeyChaincode.toUByteList(),
          networkMainnet,
          appAuthKey.toUByteList(),
          serverSpendingKey.toUByteList(),
          serverSpendingKeyChaincode.toUByteList(),
          wsmSignature.toUByteList(),
          accountIndex
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: SignatureState.Data -> state.response },
      generateResult = { state: SignatureState.Result -> state.value }
    )

  /**
   * Maximum number of inputs/outputs supported by the one-shot sign_tx_request_cmd.
   * Transactions exceeding this use the streaming signing protocol.
   */
  private companion object {
    const val MAX_SIGN_TX_ENTRIES = 5
    const val STREAM_CHUNK_SIZE = 452

    /**
     * Signatures per NFC round-trip for batched retrieval.
     * Each TxSignatureEntry is ~112 bytes (33 pubkey + ~72 DER sig + proto tags).
     * Must stay below MAX_PROTO_SIZE (505 bytes) including the WalletRsp wrapper.
     * 4 × 112 + ~10 overhead ≈ 458 bytes — safely within the limit.
     */
    const val SIGNATURE_BATCH_SIZE = 4u
  }

  /**
   * Sign a transaction on W3 hardware using the non-PSBT signing protocol.
   *
   * For transactions with ≤5 inputs AND ≤5 outputs, uses the one-shot
   * `sign_tx_request_cmd` which fits in a single proto message.
   *
   * For larger transactions, uses the streaming protocol:
   * 1. First tap: decomposes PSBT, streams canonical binary payload in 452-byte chunks,
   *    then finalizes with commitment hash → gets back confirmation handles
   * 2. Second tap: calls getConfirmationResult → firmware signals signatures ready,
   *    then retrieves per-input signatures via get_tx_signature_cmd
   *
   * @param session the active NFC session
   * @param psbt the PSBT to sign
   * @param spendingKeyset the spending keyset containing hardware fingerprint
   * @return HardwareInteraction that resolves to the signed PSBT
   */
  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
    displayPreference: HwDisplayPreference?,
  ): HardwareInteraction<Psbt> {
    // Decompose the PSBT into raw transaction fields for the non-PSBT signing protocol.
    val decomposed = try {
      decomposePsbt(
        psbtBase64 = psbt.base64,
        originFingerprint = spendingKeyset.hardwareKey.key.origin.fingerprint
      )
    } catch (e: CommandException) {
      throw NfcException.CommandError(
        message = "Failed to decompose PSBT: ${e.message}",
        cause = e
      )
    }

    // Map display preferences to FFI types. Default to satoshi if not provided.
    val ffiBtcUnit = displayPreference?.bitcoinDisplayUnit.toFfi()

    // Route to streaming or one-shot based on input/output count.
    val needsStreaming = decomposed.inputs.size > MAX_SIGN_TX_ENTRIES ||
      decomposed.outputs.size > MAX_SIGN_TX_ENTRIES
    return if (needsStreaming) {
      signTransactionStreaming(psbt, decomposed, ffiBtcUnit)
    } else {
      signTransactionOneShot(session, psbt, decomposed, ffiBtcUnit)
    }
  }

  /**
   * One-shot signing for small transactions (≤5 inputs, ≤5 outputs).
   * Uses the existing sign_tx_request_cmd which fits in a single proto message.
   */
  @Suppress("ThrowsCount")
  private suspend fun signTransactionOneShot(
    session: NfcSession,
    psbt: Psbt,
    decomposed: DecomposedPsbt,
    btcDisplayUnit: FfiBtcDisplayUnit,
  ): HardwareInteraction<Psbt> {
    val result = executeCommand(
      session = session,
      generateCommand = {
        SignTxRequest(decomposed.version, decomposed.lockTime, decomposed.inputs, decomposed.outputs, btcDisplayUnit)
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: SignTxRequestResultState.Data -> state.response },
      generateResult = { state: SignTxRequestResultState.Result -> state.value }
    )

    return when (result) {
      is SignTxRequestResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<Psbt> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.SignTx -> {
                val ffiSignatures = confirmResult.signatures.map { sig ->
                  FfiInputSignatureTuple(
                    inputIndex = sig.inputIndex,
                    publicKey = sig.publicKey,
                    signature = sig.signature
                  )
                }
                val signedBase64 = try {
                  assemblePsbtSignatures(
                    psbtBase64 = psbt.base64,
                    signatures = ffiSignatures,
                    allowUnfinalized = false
                  )
                } catch (e: CommandException) {
                  throw NfcException.CommandError(
                    message = "Failed to assemble PSBT signatures: ${e.message}",
                    cause = e
                  )
                }
                HardwareInteraction.Completed(psbt.copy(base64 = signedBase64))
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "signTransaction expected SignTx result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  /**
   * Streaming signing for large transactions (>5 inputs or >5 outputs).
   *
   * Returns [HardwareInteraction.RequiresTransfer] so the state machine's progress
   * pipeline captures chunk upload progress (same visual pattern as FWUP).
   *
   * Flow:
   * 1. [First tap] RequiresTransfer callback streams the payload:
   *    sign_stream_start_cmd → sign_stream_transfer_cmd × N chunks → sign_stream_finalize_cmd
   *    → returns RequiresConfirmation
   * 2. [User confirms on device]
   * 3. [Second tap] get_confirmation_result → SignStreamReady(num_inputs)
   *    → RequiresTransfer callback retrieves per-input signatures via get_tx_signature_cmd
   *    → returns Completed
   */
  private fun signTransactionStreaming(
    psbt: Psbt,
    decomposed: DecomposedPsbt,
    btcDisplayUnit: FfiBtcDisplayUnit,
  ): HardwareInteraction<Psbt> {
    // Serialize the canonical binary payload and compute commitment hash eagerly
    // so errors surface immediately, not inside the transfer callback.
    val streamPayload = try {
      serializeSignStreamPayload(
        decomposed.version,
        decomposed.lockTime,
        decomposed.inputs,
        decomposed.outputs
      )
    } catch (e: CommandException) {
      throw NfcException.CommandError(
        message = "Failed to serialize stream payload: ${e.message}",
        cause = e
      )
    }

    // Return RequiresTransfer — the state machine will call transferAndFetch with
    // an onProgress callback, which drives the progress bar UI.
    return HardwareInteraction.RequiresTransfer { transferSession, _, onProgress ->
      streamPayloadAndFinalize(
        session = transferSession,
        psbt = psbt,
        decomposed = decomposed,
        streamPayload = streamPayload,
        onProgress = onProgress,
        btcDisplayUnit = btcDisplayUnit
      )
    }
  }

  /**
   * Streams the canonical binary payload to the device and finalizes the session.
   * Called inside a RequiresTransfer callback with progress reporting.
   *
   * Returns [HardwareInteraction.RequiresConfirmation] for the two-tap confirmation flow.
   */
  @Suppress("ThrowsCount")
  private suspend fun streamPayloadAndFinalize(
    session: NfcSession,
    psbt: Psbt,
    decomposed: DecomposedPsbt,
    streamPayload: StreamPayload,
    onProgress: NfcProgressCallback,
    btcDisplayUnit: FfiBtcDisplayUnit,
  ): HardwareInteraction<Psbt> {
    // Step 1: Start the streaming session
    val startResult = executeCommand(
      session = session,
      generateCommand = {
        SignStreamStart(
          decomposed.inputs.size.toUInt(),
          decomposed.outputs.size.toUInt(),
          decomposed.version,
          decomposed.lockTime,
          streamPayload.payloadSize,
          btcDisplayUnit
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: SignStreamStartResultState.Data -> state.response },
      generateResult = { state: SignStreamStartResultState.Result -> state.value }
    )
    if (startResult != SignStreamStartResult.SUCCESS) {
      throw NfcException.CommandError(
        message = "sign_stream_start failed: $startResult"
      )
    }

    // Step 2: Stream the payload in 452-byte chunks with progress reporting
    val payloadBytes = streamPayload.data
    val totalChunks = (payloadBytes.size + STREAM_CHUNK_SIZE - 1) / STREAM_CHUNK_SIZE
    for (chunkIndex in 0 until totalChunks) {
      val offset = chunkIndex * STREAM_CHUNK_SIZE
      val end = minOf(offset + STREAM_CHUNK_SIZE, payloadBytes.size)
      val chunkData = payloadBytes.subList(offset, end)

      executeCommand(
        session = session,
        generateCommand = {
          SignStreamTransfer(chunkIndex.toUInt(), chunkData)
        },
        getNext = { command, data -> command.next(data) },
        getResponse = { state: SignStreamTransferResultState.Data -> state.response },
        generateResult = { state: SignStreamTransferResultState.Result -> state.value }
      )

      // Report chunk upload progress (0.0 → 1.0)
      onProgress.onProgress((chunkIndex.toFloat() + 1f) / totalChunks.toFloat())
    }

    // Step 3: Finalize with commitment hash → CONFIRMATION_PENDING
    val finalizeResult = executeCommand(
      session = session,
      generateCommand = {
        SignStreamFinalize(streamPayload.commitmentHash)
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: SignStreamFinalizeResultState.Data -> state.response },
      generateResult = { state: SignStreamFinalizeResultState.Result -> state.value }
    )

    return when (finalizeResult) {
      is SignStreamFinalizeResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = finalizeResult.responseHandle,
          confirmationHandle = finalizeResult.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<Psbt> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.SignStreamReady -> {
                // Return RequiresTransfer so the state machine's continuation
                // retrieves per-input signatures with progress in the same NFC session.
                HardwareInteraction.RequiresTransfer<Psbt> { signatureSession, _, sigProgress ->
                  retrieveStreamingSignatures(
                    session = signatureSession,
                    psbt = psbt,
                    numInputs = confirmResult.numInputs,
                    onProgress = sigProgress,
                    allowUnfinalized = false
                  )
                }
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "signTransaction (streaming) expected SignStreamReady but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  /**
   * W3 sweep signing. Used to move UTXOs from an OLD keyset to the current
   * account's fresh address (index 0) after an account-bumping recovery.
   *
   * Structurally parallel to [signTransaction]: decomposes the PSBT, routes
   * one-shot vs streaming on entry count, reuses the confirmation flow and
   * streaming transfer/finalize/signature-retrieval machinery. The only
   * difference is the initial start command, which carries the OLD account
   * index + OLD app/server xpubs so firmware can reconstruct the correct
   * witness script for the old account's UTXOs.
   */
  override suspend fun sweepTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
    sweepContext: SweepSigningContext,
    displayPreference: HwDisplayPreference?,
  ): HardwareInteraction<Psbt> {
    val decomposed = try {
      decomposePsbt(
        psbtBase64 = psbt.base64,
        originFingerprint = spendingKeyset.hardwareKey.key.origin.fingerprint
      )
    } catch (e: CommandException) {
      throw NfcException.CommandError(
        message = "Failed to decompose PSBT: ${e.message}",
        cause = e
      )
    }

    val ffiBtcUnit = displayPreference?.bitcoinDisplayUnit.toFfi()

    val needsStreaming = decomposed.inputs.size > MAX_SIGN_TX_ENTRIES ||
      decomposed.outputs.size > MAX_SIGN_TX_ENTRIES
    return if (needsStreaming) {
      sweepTransactionStreaming(psbt, decomposed, sweepContext, ffiBtcUnit)
    } else {
      sweepTransactionOneShot(session, psbt, decomposed, sweepContext, ffiBtcUnit)
    }
  }

  @Suppress("ThrowsCount")
  private suspend fun sweepTransactionOneShot(
    session: NfcSession,
    psbt: Psbt,
    decomposed: DecomposedPsbt,
    sweepContext: SweepSigningContext,
    btcDisplayUnit: FfiBtcDisplayUnit,
  ): HardwareInteraction<Psbt> {
    val result = executeCommand(
      session = session,
      generateCommand = {
        SweepSignRequest(
          sweepContext.oldAccountIndex,
          sweepContext.oldAppXpub.toFfi(),
          sweepContext.oldServerXpub.toFfi(),
          decomposed.version,
          decomposed.lockTime,
          decomposed.inputs,
          decomposed.outputs,
          btcDisplayUnit
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: SignTxRequestResultState.Data -> state.response },
      generateResult = { state: SignTxRequestResultState.Result -> state.value }
    )

    return when (result) {
      is SignTxRequestResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<Psbt> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.SignTx -> {
                val ffiSignatures = confirmResult.signatures.map { sig ->
                  FfiInputSignatureTuple(
                    inputIndex = sig.inputIndex,
                    publicKey = sig.publicKey,
                    signature = sig.signature
                  )
                }
                val signedBase64 = try {
                  assemblePsbtSignatures(
                    psbtBase64 = psbt.base64,
                    signatures = ffiSignatures,
                    allowUnfinalized = true
                  )
                } catch (e: CommandException) {
                  throw NfcException.CommandError(
                    message = "Failed to assemble sweep PSBT signatures: ${e.message}",
                    cause = e
                  )
                }
                HardwareInteraction.Completed(psbt.copy(base64 = signedBase64))
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "sweepTransaction expected SignTx result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  private fun sweepTransactionStreaming(
    psbt: Psbt,
    decomposed: DecomposedPsbt,
    sweepContext: SweepSigningContext,
    btcDisplayUnit: FfiBtcDisplayUnit,
  ): HardwareInteraction<Psbt> {
    val streamPayload = try {
      serializeSignStreamPayload(
        decomposed.version,
        decomposed.lockTime,
        decomposed.inputs,
        decomposed.outputs
      )
    } catch (e: CommandException) {
      throw NfcException.CommandError(
        message = "Failed to serialize sweep stream payload: ${e.message}",
        cause = e
      )
    }

    return HardwareInteraction.RequiresTransfer { transferSession, _, onProgress ->
      streamSweepPayloadAndFinalize(
        session = transferSession,
        psbt = psbt,
        decomposed = decomposed,
        streamPayload = streamPayload,
        sweepContext = sweepContext,
        onProgress = onProgress,
        btcDisplayUnit = btcDisplayUnit
      )
    }
  }

  @Suppress("ThrowsCount")
  private suspend fun streamSweepPayloadAndFinalize(
    session: NfcSession,
    psbt: Psbt,
    decomposed: DecomposedPsbt,
    streamPayload: StreamPayload,
    sweepContext: SweepSigningContext,
    onProgress: NfcProgressCallback,
    btcDisplayUnit: FfiBtcDisplayUnit,
  ): HardwareInteraction<Psbt> {
    // Step 1: sweep-flavored stream start
    val startResult = executeCommand(
      session = session,
      generateCommand = {
        SweepSignStreamStart(
          sweepContext.oldAccountIndex,
          sweepContext.oldAppXpub.toFfi(),
          sweepContext.oldServerXpub.toFfi(),
          decomposed.inputs.size.toUInt(),
          decomposed.outputs.size.toUInt(),
          decomposed.version,
          decomposed.lockTime,
          streamPayload.payloadSize,
          btcDisplayUnit
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: SweepSignStreamStartResultState.Data -> state.response },
      generateResult = { state: SweepSignStreamStartResultState.Result -> state.value }
    )
    if (startResult != SweepSignStreamStartResult.SUCCESS) {
      throw NfcException.CommandError(
        message = "sweep_sign_stream_start failed: $startResult"
      )
    }

    // Steps 2/3 reuse the regular streaming transfer + finalize commands —
    // firmware stores the sweep context on the stream session and the
    // signer applies it when producing per-input signatures.
    val payloadBytes = streamPayload.data
    val totalChunks = (payloadBytes.size + STREAM_CHUNK_SIZE - 1) / STREAM_CHUNK_SIZE
    for (chunkIndex in 0 until totalChunks) {
      val offset = chunkIndex * STREAM_CHUNK_SIZE
      val end = minOf(offset + STREAM_CHUNK_SIZE, payloadBytes.size)
      val chunkData = payloadBytes.subList(offset, end)

      executeCommand(
        session = session,
        generateCommand = {
          SignStreamTransfer(chunkIndex.toUInt(), chunkData)
        },
        getNext = { command, data -> command.next(data) },
        getResponse = { state: SignStreamTransferResultState.Data -> state.response },
        generateResult = { state: SignStreamTransferResultState.Result -> state.value }
      )
      onProgress.onProgress((chunkIndex.toFloat() + 1f) / totalChunks.toFloat())
    }

    val finalizeResult = executeCommand(
      session = session,
      generateCommand = {
        SignStreamFinalize(streamPayload.commitmentHash)
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: SignStreamFinalizeResultState.Data -> state.response },
      generateResult = { state: SignStreamFinalizeResultState.Result -> state.value }
    )

    return when (finalizeResult) {
      is SignStreamFinalizeResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = finalizeResult.responseHandle,
          confirmationHandle = finalizeResult.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<Psbt> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.SignStreamReady -> {
                HardwareInteraction.RequiresTransfer<Psbt> { signatureSession, _, sigProgress ->
                  retrieveStreamingSignatures(
                    session = signatureSession,
                    psbt = psbt,
                    numInputs = confirmResult.numInputs,
                    onProgress = sigProgress,
                    allowUnfinalized = true
                  )
                }
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "sweepTransaction (streaming) expected SignStreamReady but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  private fun SweepXpub.toFfi(): FfiSweepXpub {
    return FfiSweepXpub(
      pubkey = pubkey.toUByteList(),
      chaincode = chaincode.toUByteList()
    )
  }

  /**
   * Retrieves per-input signatures from the hardware after a confirmed streaming
   * signing session and assembles them into the PSBT.
   *
   * Uses batched retrieval (4 signatures per NFC round-trip) to minimize latency.
   * Deterministic ECDSA (RFC 6979) makes all calls idempotent — NFC retries are safe.
   *
   * [allowUnfinalized] is passed straight through to `assemblePsbtSignatures` —
   * `false` for regular sends (finalize is expected to succeed), `true` for
   * sweep flows (HW signs first, app + server finalize later).
   */
  private suspend fun retrieveStreamingSignatures(
    session: NfcSession,
    psbt: Psbt,
    numInputs: UInt,
    onProgress: NfcProgressCallback,
    allowUnfinalized: Boolean,
  ): HardwareInteraction<Psbt> {
    val ffiSignatures = mutableListOf<FfiInputSignatureTuple>()
    var startIndex = 0u

    while (startIndex < numInputs) {
      val batchCount = minOf(SIGNATURE_BATCH_SIZE, numInputs - startIndex)
      val batchSigs = executeCommand(
        session = session,
        generateCommand = { GetTxSignaturesBatch(startIndex, batchCount) },
        getNext = { command, data -> command.next(data) },
        getResponse = { state: TxSignaturesBatchState.Data -> state.response },
        generateResult = { state: TxSignaturesBatchState.Result -> state.`value` }
      )

      batchSigs.forEachIndexed { offset, txSig ->
        ffiSignatures.add(
          FfiInputSignatureTuple(
            inputIndex = startIndex + offset.toUInt(),
            publicKey = txSig.pubkey,
            signature = txSig.signature
          )
        )
      }

      startIndex += batchSigs.size.toUInt()
      // Report progress per batch
      onProgress.onProgress(startIndex.toFloat() / numInputs.toFloat())
    }

    // Assemble hardware signatures into the PSBT
    val signedBase64 = try {
      assemblePsbtSignatures(
        psbtBase64 = psbt.base64,
        signatures = ffiSignatures,
        allowUnfinalized = allowUnfinalized
      )
    } catch (e: CommandException) {
      throw NfcException.CommandError(
        message = "Failed to assemble streaming PSBT signatures: ${e.message}",
        cause = e
      )
    }

    return HardwareInteraction.Completed(psbt.copy(base64 = signedBase64))
  }

  /**
   * Sign an action proof on W3 hardware with user confirmation.
   *
   * W3-only feature that allows the hardware to sign a proof for actions.
   * This returns RequiresConfirmation which handles the confirmation flow.
   *
   * @param session the active NFC session
   * @param version the version of the action proof
   * @param action the action type
   * @param value the new value (if applicable)
   * @param bindings the bindings for the proof
   * @return HardwareInteraction that manages the W3 signing flow
   */
  override suspend fun signActionProof(
    session: NfcSession,
    version: UInt,
    action: ActionProofAction,
    value: String?,
    bindings: String,
  ): HardwareInteraction<String> {
    val result = executeCommand(
      session = session,
      generateCommand = {
        SignActionProof(version, action.toPascalCase(), value, bindings)
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: SignActionProofResultState.Data -> state.response },
      generateResult = { state: SignActionProofResultState.Result -> state.value }
    )
    return when (result) {
      is SignActionProofResult.Success ->
        HardwareInteraction.Completed(result.signature.toByteString().hex())
      is SignActionProofResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<String> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.SignActionProof ->
                HardwareInteraction.Completed(confirmResult.signature)
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "signActionProof expected SignActionProof result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  /**
   * Rotate app auth keys composite command.
   *
   * First tap: sends [RotateAppAuthKeys] command → gets ConfirmationPending.
   * Second tap (after user confirms on device): getConfirmationResult returns
   *   RotateAppAuthKeys with all signatures (action proof, signed account ID,
   *   app auth key signature, HW auth public key).
   */
  override suspend fun rotateAppAuthKeys(
    session: NfcSession,
    params: RotateAppAuthKeysContinueParams,
  ): HardwareInteraction<RotateAppAuthKeysCompositeResult> {
    val result = executeCommand(
      session = session,
      generateCommand = {
        FfiRotateAppAuthKeys(
          params.actionProofVersion,
          params.actionProofAction.toPascalCase(),
          null,
          params.actionProofBindings,
          params.accountId,
          params.appGlobalAuthPublicKey
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: FfiRotateAppAuthKeysResultState.Data -> state.response },
      generateResult = { state: FfiRotateAppAuthKeysResultState.Result -> state.value }
    )

    return when (result) {
      is FfiRotateAppAuthKeysResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<RotateAppAuthKeysCompositeResult> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.RotateAppAuthKeys -> {
                HardwareInteraction.Completed(
                  RotateAppAuthKeysCompositeResult(
                    actionProofSignature = confirmResult.actionProofSignature.toByteString().hex(),
                    hwSignedAccountId = signatureUtils.encodeSignatureToDer(confirmResult.hwSignedAccountId.toUByteArray().toByteArray()).hex(),
                    appGlobalAuthKeyHwSignature = signatureUtils.encodeSignatureToDer(confirmResult.appAuthKeySignature.toUByteArray().toByteArray()).hex(),
                    hwAuthPublicKey = HwAuthPublicKey(
                      Secp256k1PublicKey(confirmResult.hwAuthPublicKey.toByteString().hex())
                    )
                  )
                )
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "rotateAppAuthKeys expected RotateAppAuthKeys result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  /**
   * Upgrade rotate app auth keys (W3 upgrade flow, no action proof signing).
   */
  override suspend fun upgradeRotateAppAuthKeys(
    session: NfcSession,
    params: UpgradeRotateAppAuthKeysParams,
  ): HardwareInteraction<UpgradeRotateAppAuthKeysResult> {
    val result = executeCommand(
      session = session,
      generateCommand = {
        FfiUpgradeRotateAppAuthKeys(
          params.accountId,
          params.appGlobalAuthPublicKey
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: FfiUpgradeRotateAppAuthKeysResultState.Data -> state.response },
      generateResult = { state: FfiUpgradeRotateAppAuthKeysResultState.Result -> state.value }
    )

    return when (result) {
      is FfiUpgradeRotateAppAuthKeysResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<UpgradeRotateAppAuthKeysResult> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.UpgradeRotateAppAuthKeys -> {
                HardwareInteraction.Completed(
                  UpgradeRotateAppAuthKeysResult(
                    hwSignedAccountId = signatureUtils.encodeSignatureToDer(confirmResult.hwSignedAccountId.toUByteArray().toByteArray()).hex(),
                    appGlobalAuthKeyHwSignature = signatureUtils.encodeSignatureToDer(confirmResult.appAuthKeySignature.toUByteArray().toByteArray()).hex(),
                    hwAuthPublicKey = HwAuthPublicKey(
                      Secp256k1PublicKey(confirmResult.hwAuthPublicKey.toByteString().hex())
                    )
                  )
                )
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "upgradeRotateAppAuthKeys expected UpgradeRotateAppAuthKeys result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  /**
   * Signs a challenge with user confirmation during lost app recovery (W3 only).
   *
   * First tap: sends challenge → firmware shows confirmation prompt → CONFIRMATION_PENDING.
   * Second tap: getConfirmationResult returns the signature.
   */
  override suspend fun lostAppRecoverySignChallenge(
    session: NfcSession,
    challenge: ByteString,
  ): HardwareInteraction<String> {
    val result = executeCommand(
      session = session,
      generateCommand = { LostAppRecoverySignChallenge(challenge.toUByteList()) },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: LostAppRecoverySignChallengeResultState.Data -> state.response },
      generateResult = { state: LostAppRecoverySignChallengeResultState.Result -> state.value }
    )

    return when (result) {
      is LostAppRecoverySignChallengeResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<String> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.LostAppRecoverySignChallenge ->
                HardwareInteraction.Completed(confirmResult.signature)
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "lostAppRecoverySignChallenge expected LostAppRecoverySignChallenge result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  /**
   * Lost app recovery composite command.
   *
   * First tap: sends sealed SSEK → gets ConfirmationPending.
   * Second tap (after user confirms on device): getConfirmationResult returns LostAppRecoverySsek,
   *   the mapper returns RequiresTransfer which runs the async callback + continue command
   *   within the same NFC session.
   */
  override suspend fun lostAppRecovery(
    session: NfcSession,
    sealedSsek: ByteString,
    onSsekUnsealed: suspend (SymmetricKey) -> LostAppRecoveryContinueParams,
  ): HardwareInteraction<LostAppRecoveryCompositeResult> {
    val result = executeCommand(
      session = session,
      generateCommand = { LostAppRecovery(sealedSsek.toUByteList()) },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: LostAppRecoveryResultState.Data -> state.response },
      generateResult = { state: LostAppRecoveryResultState.Result -> state.value }
    )

    return when (result) {
      is LostAppRecoveryResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<LostAppRecoveryCompositeResult> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.LostAppRecoverySsek -> {
                // Return RequiresTransfer to do async work + continue command in next NFC session
                HardwareInteraction.RequiresTransfer { transferSession, transferCommands, _ ->
                  executeLostAppRecoveryContinue(
                    session = transferSession,
                    unsealedSsek = SymmetricKeyImpl(confirmResult.ssek.toByteString()),
                    onSsekUnsealed = onSsekUnsealed
                  )
                }
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "lostAppRecovery expected LostAppRecoverySsek result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  /**
   * EEK restoration unseal symmetric key on W3 hardware with user confirmation.
   *
   * First tap: sends sealed key → firmware shows "Decrypt your Emergency Exit Kit backup?"
   * → returns RequiresConfirmation.
   * Second tap: getConfirmationResult returns EekRestorationUnsealSymmetricKey with the key.
   */
  @OptIn(PrivateData::class)
  override suspend fun eekRestorationUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ): HardwareInteraction<SymmetricKey> {
    val result = executeCommand(
      session = session,
      generateCommand = { EekRestorationUnseal(sealedKey.toUByteList()) },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: EekRestorationUnsealResultState.Data -> state.response },
      generateResult = { state: EekRestorationUnsealResultState.Result -> state.value }
    )

    return when (result) {
      is EekRestorationUnsealResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<SymmetricKey> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.EekRestorationUnsealSymmetricKey ->
                HardwareInteraction.Completed(
                  SymmetricKeyImpl(confirmResult.unsealedKey.toByteString())
                )
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "eekRestorationUnsealSymmetricKey expected EekRestorationUnsealSymmetricKey result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  /**
   * Full account cloud backup restoration on W3 hardware with user confirmation.
   *
   * First tap: firmware shows "Decrypt your wallet backups?" → returns RequiresConfirmation.
   * Second tap: streams sealed CSEKs to firmware one at a time. When firmware successfully
   * unseals one, returns the unsealed key and its index via [onCsekUnsealed].
   */
  override suspend fun <T> fullAccountCloudBackupRestoration(
    session: NfcSession,
    sealedCseks: List<SealedData>,
    onCsekUnsealed: suspend (CsekUnsealResult) -> T,
  ): HardwareInteraction<T> {
    val result = executeCommand(
      session = session,
      generateCommand = { FullAccountCloudBackupRestoration() },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: FullAccountCloudBackupRestorationResultState.Data -> state.response },
      generateResult = { state: FullAccountCloudBackupRestorationResultState.Result -> state.value }
    )

    return when (result) {
      is FullAccountCloudBackupRestorationResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<T> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.FullAccountCloudBackupRestoration -> {
                // Session confirmed — stream sealed CSEKs to firmware until one succeeds.
                HardwareInteraction.RequiresTransfer { transferSession, _, _ ->
                  val unsealResult = streamCseksToFirmware(
                    transferSession, sealedCseks, handles.responseHandle
                  )
                  HardwareInteraction.Completed(onCsekUnsealed(unsealResult))
                }
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "fullAccountCloudBackupRestoration expected FullAccountCloudBackupRestoration result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  /**
   * Streams sealed CSEKs to firmware one at a time within a confirmed restoration session.
   * Each CSEK is sent with its index. On the first success, firmware returns the unsealed
   * key and echoes the index back. Throws if none can be unsealed.
   *
   * Only unseal-mismatch errors are caught and retried with the next CSEK.
   * Session/transport errors (tag lost, timeout, auth) propagate immediately.
   */
  private suspend fun streamCseksToFirmware(
    session: NfcSession,
    sealedCseks: List<SealedData>,
    sessionToken: List<UByte>,
  ): CsekUnsealResult {
    var lastError: NfcException? = null
    var unsealMismatch: NfcException.CommandErrorSealCsekResponseUnsealException? = null
    for ((index, sealedCsek) in sealedCseks.withIndex()) {
      try {
        return unsealCsekInRestorationSession(session, index, sealedCsek, sessionToken)
      } catch (e: NfcException.CommandErrorSealCsekResponseUnsealException) {
        lastError = e
        unsealMismatch = e
        // Firmware couldn't unseal this CSEK, try the next one
      } catch (e: NfcException.CommandError) {
        lastError = e
        // Generic command error during unseal — try the next CSEK
      }
    }
    // If any iteration produced a genuine unseal-mismatch, that is the canonical
    // "hardware can't decrypt these CSEKs" signal — surface it. Otherwise
    // rethrow the last generic command error as-is so real firmware/state
    // failures aren't masked as a decrypt mismatch. If `sealedCseks` was empty
    // the loop never ran, so neither variable was set; throw a generic
    // command error instead of fabricating an unseal-mismatch the user never
    // experienced (that would mis-route into the W-17080 blocking UX).
    throw unsealMismatch
      ?: lastError
      ?: NfcException.CommandError(message = "Could not unseal any CSEK with this hardware")
  }

  /**
   * Sends a single sealed CSEK with its index to firmware for unsealing within a confirmed
   * cloud backup restoration session. Firmware attempts to unseal and returns the key
   * along with the index on success, or throws on failure.
   *
   * @param session the active NFC session (same session as the confirmation)
   * @param index zero-based index of this CSEK in the caller's candidate list
   * @param sealedCsek the sealed CSEK to unseal
   * @return [CsekUnsealResult] with the unsealed key and index
   */
  @OptIn(PrivateData::class)
  private suspend fun unsealCsekInRestorationSession(
    session: NfcSession,
    index: Int,
    sealedCsek: SealedData,
    sessionToken: List<UByte>,
  ): CsekUnsealResult {
    val result = executeCommand(
      session = session,
      generateCommand = {
        FullAccountCloudBackupRestorationContinue(
          sealedCsek.toUByteList(),
          index.toUInt(),
          sessionToken
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = {
          state: FullAccountCloudBackupRestorationContinueResultState.Data ->
        state.response
      },
      generateResult = {
          state: FullAccountCloudBackupRestorationContinueResultState.Result ->
        state.value
      }
    )
    return CsekUnsealResult(
      index = result.csekIndex.toInt(),
      unsealedCsek = SymmetricKeyImpl(result.unsealedCsek.toByteString())
    )
  }

  /**
   * Executes the continue phase of lost app recovery: calls the [onSsekUnsealed] callback
   * to decrypt descriptors and build continue params, then sends the continue command.
   */
  @Suppress("ThrowsCount")
  private suspend fun executeLostAppRecoveryContinue(
    session: NfcSession,
    unsealedSsek: SymmetricKey,
    onSsekUnsealed: suspend (SymmetricKey) -> LostAppRecoveryContinueParams,
  ): HardwareInteraction<LostAppRecoveryCompositeResult> {
    val params = onSsekUnsealed(unsealedSsek)
    val continueResult = executeCommand(
      session = session,
      generateCommand = {
        LostAppRecoveryContinue(
          actionProofVersion = params.actionProofVersion,
          action = params.actionProofAction.toPascalCase(),
          value = null,
          bindings = params.actionProofBindings,
          existingDescriptorPublicKeys = params.existingHwSpendingKeys.map { it.key.dpub },
          network = params.network.toFfiBtcNetwork(),
          appGlobalAuthKey = params.appGlobalAuthKey.value.decodeHex().toUByteList()
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: LostAppRecoveryContinueResultState.Data -> state.response },
      generateResult = { state: LostAppRecoveryContinueResultState.Result -> state.value }
    )
    return HardwareInteraction.Completed(
      LostAppRecoveryCompositeResult(
        actionProofSignature = continueResult.actionProofSignature.toByteString().hex(),
        spendingKeyDpub = DescriptorPublicKey(continueResult.spendingKeyDpub),
        appAuthKeySignature = continueResult.appAuthKeySignature.toByteString().hex()
      )
    )
  }

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ): String {
    throw NfcException.FeatureNotSupported()
  }

  override suspend fun signChallengeAndSealSeks(
    session: NfcSession,
    challenge: ByteString,
    unsealedCsek: ByteString,
    unsealedSsek: ByteString,
  ): HardwareInteraction<SignChallengeAndSealSeksResult> {
    val result = executeCommand(
      session = session,
      generateCommand = {
        SignChallengeAndSealSeks(
          challenge.toUByteList(),
          unsealedCsek.toUByteList(),
          unsealedSsek.toUByteList()
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: SignChallengeAndSealSeksResultState.Data -> state.response },
      generateResult = { state: SignChallengeAndSealSeksResultState.Result -> state.value }
    )

    return when (result) {
      is FfiSignChallengeAndSealSeksResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<SignChallengeAndSealSeksResult> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.SignChallengeAndSealSeks -> {
                HardwareInteraction.Completed(
                  SignChallengeAndSealSeksResult(
                    signedChallenge = confirmResult.signature.toByteString().hex(),
                    sealedCsek = confirmResult.sealedCsek.toByteString(),
                    sealedSsek = confirmResult.sealedSsek.toByteString()
                  )
                )
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "signChallengeAndSealSeks expected SignChallengeAndSealSeks result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  override suspend fun recoveryAuthorizeLostApp(
    session: NfcSession,
    sealedDdkData: SealedData?,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<RecoveryAuthorizeLostAppResult> {
    val result = executeCommand(
      session = session,
      generateCommand = {
        RecoveryAuthorizeLostApp(
          sealedDdk = sealedDdkData?.toUByteList().orEmpty(),
          sealedSsek = sealedSsekForDecryption?.toUByteList().orEmpty(),
          descriptorBackupsBindings = descriptorBackupsBindings,
          activateKeysetBindings = activateKeysetBindings,
          actionProofVersion = actionProofVersion
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: RecoveryAuthorizeLostAppResultState.Data -> state.response },
      generateResult = { state: RecoveryAuthorizeLostAppResultState.Result -> state.value }
    )

    return when (result) {
      is FfiRecoveryAuthorizeLostAppResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<RecoveryAuthorizeLostAppResult> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.RecoveryAuthorizeLostApp -> {
                HardwareInteraction.Completed(
                  RecoveryAuthorizeLostAppResult(
                    descriptorBackupsSignature = confirmResult.descriptorBackupsSignature.toByteString().hex(),
                    activateKeysetSignature = confirmResult.activateKeysetSignature.toByteString().hex(),
                    unsealedDdkData = confirmResult.unsealedDdkData.takeIf { it.isNotEmpty() }?.toByteString(),
                    unsealedSsek = confirmResult.unsealedSsek.takeIf { it.isNotEmpty() }?.toByteString()
                  )
                )
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "recoveryAuthorizeLostApp expected RecoveryAuthorizeLostApp result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  override suspend fun recoveryAuthorizeLostHw(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<RecoveryAuthorizeLostHwResult> {
    val result = executeCommand(
      session = session,
      generateCommand = {
        RecoveryAuthorizeLostHw(
          ddkPrivateKey = ddkPrivateKeyBytes?.toUByteList().orEmpty(),
          descriptorBackupsBindings = descriptorBackupsBindings,
          activateKeysetBindings = activateKeysetBindings,
          actionProofVersion = actionProofVersion
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: RecoveryAuthorizeLostHwResultState.Data -> state.response },
      generateResult = { state: RecoveryAuthorizeLostHwResultState.Result -> state.value }
    )

    return when (result) {
      is FfiRecoveryAuthorizeLostHwResult.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<RecoveryAuthorizeLostHwResult> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.RecoveryAuthorizeLostHw -> {
                HardwareInteraction.Completed(
                  RecoveryAuthorizeLostHwResult(
                    descriptorBackupsSignature = confirmResult.descriptorBackupsSignature.toByteString().hex(),
                    activateKeysetSignature = confirmResult.activateKeysetSignature.toByteString().hex(),
                    sealedDdkData = confirmResult.sealedDdkData.takeIf { it.isNotEmpty() }?.toByteString()
                  )
                )
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "recoveryAuthorizeLostHw expected RecoveryAuthorizeLostHw result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }

  override suspend fun upgradeAuthorizeW3(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<UpgradeAuthorizeW3Result> {
    val result = executeCommand(
      session = session,
      generateCommand = {
        FfiUpgradeAuthorizeW3(
          ddkPrivateKey = ddkPrivateKeyBytes.toUByteList(),
          sealedSsekForDecryption = sealedSsekForDecryption?.toUByteList().orEmpty(),
          descriptorBackupsBindings = descriptorBackupsBindings,
          activateKeysetBindings = activateKeysetBindings,
          actionProofVersion = actionProofVersion
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: FfiUpgradeAuthorizeW3ResultState.Data -> state.response },
      generateResult = { state: FfiUpgradeAuthorizeW3ResultState.Result -> state.value }
    )

    return when (result) {
      is FfiUpgradeAuthorizeW3Result.ConfirmationPending -> {
        val handles = ConfirmationHandles(
          responseHandle = result.responseHandle,
          confirmationHandle = result.confirmationHandle
        )
        HardwareInteraction.RequiresConfirmation(
          handles = handles,
          mapResult = confirmationResultMapper<UpgradeAuthorizeW3Result> { confirmResult ->
            when (confirmResult) {
              is ConfirmationResult.UpgradeAuthorizeW3 -> {
                HardwareInteraction.Completed(
                  UpgradeAuthorizeW3Result(
                    descriptorBackupsSignature = confirmResult.descriptorBackupsSignature.toByteString().hex(),
                    activateKeysetSignature = confirmResult.activateKeysetSignature.toByteString().hex(),
                    sealedDdkData = confirmResult.sealedDdkData.toByteString(),
                    unsealedSsek = confirmResult.unsealedSsek?.toByteString()
                  )
                )
              }
              is ConfirmationResult.Pending ->
                throw NfcException.ConfirmationPending()
              is ConfirmationResult.Denied ->
                throw NfcException.UserDenied()
              else -> throw NfcException.CommandError(
                message = "upgradeAuthorizeW3 expected UpgradeAuthorizeW3 result but got: ${confirmResult::class.simpleName}"
              )
            }
          }
        )
      }
    }
  }
}

private fun BitcoinNetworkType.toFfiBtcNetwork() =
  when (this) {
    BitcoinNetworkType.BITCOIN -> FfiBtcNetwork.BITCOIN
    BitcoinNetworkType.TESTNET -> FfiBtcNetwork.TESTNET
    BitcoinNetworkType.SIGNET -> FfiBtcNetwork.SIGNET
    BitcoinNetworkType.REGTEST -> FfiBtcNetwork.REGTEST
  }

/**
 * Maps the app's [BitcoinDisplayUnit] to the Rust FFI [FfiBtcDisplayUnit].
 * Returns [FfiBtcDisplayUnit.SATOSHI] as the default when null.
 */
private fun BitcoinDisplayUnit?.toFfi(): FfiBtcDisplayUnit =
  when (this) {
    BitcoinDisplayUnit.Satoshi, null -> FfiBtcDisplayUnit.SATOSHI
    BitcoinDisplayUnit.Bitcoin -> FfiBtcDisplayUnit.BITCOIN
  }
