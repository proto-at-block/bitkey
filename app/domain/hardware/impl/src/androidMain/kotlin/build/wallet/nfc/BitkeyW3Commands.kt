package build.wallet.nfc

import build.wallet.Progress
import build.wallet.asProgress
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.nfc.platform.ChunkData
import build.wallet.nfc.platform.ConfirmationHandles
import build.wallet.nfc.platform.ConfirmationResult
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.rust.firmware.BooleanState
import build.wallet.rust.firmware.GetAddress
import build.wallet.rust.firmware.GetAddressResultState
import build.wallet.rust.firmware.SignStart
import build.wallet.rust.firmware.SignStartResultState
import build.wallet.rust.firmware.SignTransfer
import build.wallet.rust.firmware.SignTransferResult
import build.wallet.rust.firmware.SignTransferResultState
import build.wallet.rust.firmware.VerifyKeysAndBuildDescriptor
import build.wallet.toByteString
import build.wallet.toUByteList
import com.github.michaelbull.result.getOrElse
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

/**
 * W3-specific NFC commands that delegate to the base implementation.
 *
 * Overrides W3-only features like [getAddress] which are not supported on W1 hardware.
 */
class BitkeyW3Commands(
  private val delegate: NfcCommands,
) : NfcCommands by delegate {
  companion object {
    /**
     * Maximum chunk size for PSBT transfer, defined by nanopb max_size annotation
     * on sign_transfer_cmd.chunk_data in wallet.proto.
     */
    private const val PSBT_CHUNK_SIZE = 452

    /**
     * Maximum total size for chunked responses (1 MB).
     * Safety limit to prevent infinite loops if firmware misbehaves.
     */
    private const val MAX_CHUNKED_RESPONSE_SIZE = 1_000_000
  }

  /**
   * Fetches all chunks of data from the device using the generic
   * getConfirmationResultChunk command.
   *
   * Uses chunk_index for idempotent retry - if NFC transmission fails after firmware
   * responds, the same chunk can be re-requested safely.
   *
   * @param session the active NFC session
   * @param commands NFC commands interface
   * @param handles confirmation handles from the pending operation
   * @param expectedSize expected total size from ChunkedDataAvailable (for validation)
   * @return the reassembled data as a list of bytes
   * @throws NfcException.CommandError if size exceeds safety limit or doesn't match expected
   */
  private suspend fun fetchAllChunks(
    session: NfcSession,
    commands: NfcCommands,
    handles: ConfirmationHandles,
    expectedSize: UInt,
  ): List<UByte> {
    val chunks = mutableListOf<UByte>()
    var chunkIndex = 0u
    var isLast = false

    while (!isLast) {
      val chunkData: ChunkData = commands.getConfirmationResultChunk(session, handles, chunkIndex)
      chunks.addAll(chunkData.chunk)
      isLast = chunkData.isLast
      chunkIndex++

      // Safety limit to prevent infinite loops if firmware never sets isLast
      if (chunks.size > MAX_CHUNKED_RESPONSE_SIZE) {
        throw NfcException.CommandError(
          message = "Chunked response exceeded maximum size of $MAX_CHUNKED_RESPONSE_SIZE bytes"
        )
      }
    }

    if (chunks.size.toUInt() != expectedSize) {
      throw NfcException.CommandError(
        message = "Chunked response size mismatch: expected $expectedSize bytes, received ${chunks.size}"
      )
    }

    return chunks
  }

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
  ): Boolean =
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
          wsmSignature.toUByteList()
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: BooleanState.Data -> state.response },
      generateResult = { state: BooleanState.Result -> state.value }
    )

  /**
   * Sign a transaction on W3 hardware using chunked PSBT transfer.
   *
   * W3 requires chunked transfer of the PSBT data, followed by on-device confirmation.
   * This returns RequiresTransfer which handles the chunking flow internally.
   *
   * The flow is:
   * 1. SignStart - initialize signing session with PSBT size
   * 2. SignTransfer (loop) - transfer PSBT chunks; last chunk returns ConfirmationPending
   * 3. GetConfirmationResult - returns ChunkedDataAvailable after user confirms on device on device
   * 4. GetConfirmationResultChunk (loop) - fetch signed PSBT data in chunks
   *
   * @param session the active NFC session
   * @param psbt the PSBT to sign
   * @param spendingKeyset the spending keyset containing hardware fingerprint
   * @return HardwareInteraction that manages the W3 signing flow
   */
  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ): HardwareInteraction<Psbt> {
    return HardwareInteraction.RequiresTransfer { nfcSession, _, onProgress ->
      // Decode base64 PSBT to binary ByteString for efficient transfer
      val psbtBytes = psbt.base64.decodeBase64()
        ?: throw NfcException.CommandError(message = "Failed to decode PSBT base64")

      executeCommand(
        session = nfcSession,
        generateCommand = { SignStart(psbtBytes.size.toUInt()) },
        getNext = { command, data -> command.next(data) },
        getResponse = { state: SignStartResultState.Data -> state.response },
        generateResult = { state: SignStartResultState.Result -> state.value }
      )

      val maxChunkSize = PSBT_CHUNK_SIZE

      var offset = 0
      var sequenceId = 0u
      var confirmationHandles: ConfirmationHandles? = null

      while (offset < psbtBytes.size) {
        val endIndex = minOf(offset + maxChunkSize, psbtBytes.size)
        val chunk = psbtBytes.substring(offset, endIndex)

        val transferResult = executeCommand(
          session = nfcSession,
          generateCommand = {
            SignTransfer(
              sequenceId = sequenceId,
              chunkData = chunk.toUByteList()
            )
          },
          getNext = { command, data -> command.next(data) },
          getResponse = { state: SignTransferResultState.Data -> state.response },
          generateResult = { state: SignTransferResultState.Result -> state.value }
        )

        when (transferResult) {
          is SignTransferResult.Success -> {}
          is SignTransferResult.ConfirmationPending -> {
            confirmationHandles = ConfirmationHandles(
              responseHandle = transferResult.responseHandle,
              confirmationHandle = transferResult.confirmationHandle
            )
          }
        }

        offset = endIndex
        sequenceId++
        val progressValue = (offset.toFloat() / psbtBytes.size)
          .coerceIn(0f, 1f)
          .asProgress()
          .getOrElse { Progress.Zero }
        onProgress(progressValue)
      }

      val handles = confirmationHandles
        ?: throw NfcException.CommandError(
          message = "Expected ConfirmationPending from last SignTransfer but didn't receive it. " +
            "W3 hardware must require user confirmation for transaction signing."
        )

      HardwareInteraction.RequiresConfirmation { confirmSession, confirmCommands ->
        val confirmResult = confirmCommands.getConfirmationResult(confirmSession, handles)
        when (confirmResult) {
          is ConfirmationResult.ChunkedDataAvailable -> {
            val chunks = fetchAllChunks(
              confirmSession,
              confirmCommands,
              handles,
              expectedSize = confirmResult.totalSize
            )
            val signedPsbtBase64 = chunks.toByteString().base64()
            HardwareInteraction.Completed(psbt.copy(base64 = signedPsbtBase64))
          }
          else -> throw NfcException.CommandError(
            message = "signTransaction expected ChunkedDataAvailable result but got: ${confirmResult::class.simpleName}"
          )
        }
      }
    }
  }
}
