package build.wallet.nfc.interceptors

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.SealedData
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.firmware.McuRole
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.logging.logWarn
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcException.CanBeRetried
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.ConfirmationHandles
import build.wallet.nfc.platform.ConfirmationResult
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import okio.ByteString

private const val MAX_NFC_COMMAND_RETRIES = 5
private const val IOS_TAG_RESPONSE_ERROR_MESSAGE = "Tag response error / no response"

/**
 * Retries NFC commands that are idempotent.
 */
internal fun retryCommands() =
  NfcTransactionInterceptor { next ->
    { session, commands -> next(session, RetryingNfcCommandsImpl(commands)) }
  }

/**
 * If you're here, it's probably because you're adding a new NFC command.
 *
 * This class is responsible for performing retries for our idempotent commands.
 * And almost every command is idempotent.
 *
 * Conspicuous exceptions to this rule are commands like [getEvents] and [getCoredumpFragment],
 * which download and delete data from the Bitkey hardware. Sending them multiple times,
 * without the caller knowing, would result in incorrect behaviour.
 */
private class RetryingNfcCommandsImpl(
  private val commands: NfcCommands,
) : NfcCommands {
  override suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
    mcuRole: McuRole,
  ): HardwareInteraction<Boolean> =
    wrapHardwareInteraction(retry { commands.fwupStart(session, patchSize, fwupMode, mcuRole) })

  override suspend fun fwupTransfer(
    session: NfcSession,
    sequenceId: UInt,
    fwupData: List<UByte>,
    offset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole,
  ): Boolean {
    // TODO(W-8001): This intentionally does not retry for now.
    // See: https://sq-block.slack.com/archives/C043X6LRLJX/p1713568061850029?thread_ts=1713568055.125989&cid=C043X6LRLJX
    return commands.fwupTransfer(session, sequenceId, fwupData, offset, fwupMode, mcuRole)
  }

  override suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole,
  ) = try {
    commands.fwupFinish(session, appPropertiesOffset, signatureOffset, fwupMode, mcuRole)
  } catch (e: CanBeRetried.TransceiveFailure) {
    // For some iOS devices: If we get a "Tag response error", it might actually be success
    // since the device resets immediately after sending the response and before the
    // mobile app can read it. We treat "TransceiveFailure" containing "Tag response error"
    // as WillApplyPatch since the firmware transfer completed successfully (we reached fwupFinish step).
    if (e.message == IOS_TAG_RESPONSE_ERROR_MESSAGE) {
      logWarn(tag = "NFC", throwable = e) {
        "fwupFinish failed with error ${e.message} - treating as success since firmware transfer completed - mode: ${fwupMode.name}"
      }
      // Return WillApplyPatch to indicate firmware will apply the update asynchronously
      // This matches the expected firmware behavior where device resets after responding
      FwupFinishResponseStatus.WillApplyPatch
    } else {
      // Do not retry fwupFinish for other errors - this command is not idempotent.
      // The firmware may have already applied the update, and retrying could cause undefined behavior
      logWarn(tag = "NFC", throwable = e) { "fwupFinish TransceiveFailure - mode: ${fwupMode.name}" }
      throw e
    }
  } catch (e: NfcException) {
    logWarn(tag = "NFC", throwable = e) { "fwupFinish failed - mode: ${fwupMode.name}" }
    throw e
  }

  override suspend fun getAuthenticationKey(session: NfcSession) =
    retry { commands.getAuthenticationKey(session) }

  override suspend fun getCoredumpCount(session: NfcSession) =
    retry { commands.getCoredumpCount(session) }

  override suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
    mcuRole: McuRole,
  ) = commands.getCoredumpFragment(session, offset, mcuRole)

  override suspend fun getDeviceInfo(session: NfcSession) =
    retry { commands.getDeviceInfo(session) }

  override suspend fun getEvents(
    session: NfcSession,
    mcuRole: McuRole,
  ) = commands.getEvents(session, mcuRole)

  override suspend fun getFirmwareFeatureFlags(session: NfcSession): List<FirmwareFeatureFlagCfg> =
    retry { commands.getFirmwareFeatureFlags(session) }

  override suspend fun getFirmwareMetadata(session: NfcSession) =
    retry { commands.getFirmwareMetadata(session) }

  override suspend fun getFingerprintEnrollmentStatus(
    session: NfcSession,
    isEnrollmentContextAware: Boolean,
  ) = retry { commands.getFingerprintEnrollmentStatus(session, isEnrollmentContextAware) }

  override suspend fun deleteFingerprint(
    session: NfcSession,
    index: Int,
  ) = retry { commands.deleteFingerprint(session, index) }

  override suspend fun getUnlockMethod(session: NfcSession) =
    retry { commands.getUnlockMethod(session) }

  override suspend fun cancelFingerprintEnrollment(session: NfcSession): Boolean =
    retry { commands.cancelFingerprintEnrollment(session) }

  override suspend fun getEnrolledFingerprints(session: NfcSession): EnrolledFingerprints =
    retry { commands.getEnrolledFingerprints(session) }

  override suspend fun setFingerprintLabel(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = retry { commands.setFingerprintLabel(session, fingerprintHandle) }

  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = retry { commands.getInitialSpendingKey(session, network) }

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ) = retry { commands.getNextSpendingKey(session, existingDescriptorPublicKeys, network) }

  override suspend fun lockDevice(session: NfcSession) = retry { commands.lockDevice(session) }

  override suspend fun queryAuthentication(session: NfcSession) =
    retry { commands.queryAuthentication(session) }

  override suspend fun sealData(
    session: NfcSession,
    unsealedData: ByteString,
  ) = retry { commands.sealData(session, unsealedData) }

  override suspend fun unsealData(
    session: NfcSession,
    sealedData: SealedData,
  ) = retry { commands.unsealData(session, sealedData) }

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ) = retry { commands.signChallenge(session, challenge) }

  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ): HardwareInteraction<Psbt> =
    wrapHardwareInteraction(retry { commands.signTransaction(session, psbt, spendingKeyset) })

  override suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = retry { commands.startFingerprintEnrollment(session, fingerprintHandle) }

  override suspend fun version(session: NfcSession) = retry { commands.version(session) }

  override suspend fun wipeDevice(session: NfcSession): HardwareInteraction<Boolean> =
    wrapHardwareInteraction(retry { commands.wipeDevice(session) })

  override suspend fun getCert(
    session: NfcSession,
    certType: FirmwareCertType,
  ): List<UByte> = retry { commands.getCert(session, certType) }

  override suspend fun signVerifyAttestationChallenge(
    session: NfcSession,
    deviceIdentityDer: List<UByte>,
    challenge: List<UByte>,
  ): Boolean =
    retry {
      commands.signVerifyAttestationChallenge(
        session,
        deviceIdentityDer,
        challenge
      )
    }

  override suspend fun getGrantRequest(
    session: NfcSession,
    action: GrantAction,
  ): GrantRequest {
    // Not retried: Each call generates a new request on firmware, overwriting the previous one.
    return commands.getGrantRequest(session, action)
  }

  override suspend fun provideGrant(
    session: NfcSession,
    grant: Grant,
  ): Boolean {
    // Not retried: Firmware deletes its stored GrantRequest after the first attempt to process a Grant.
    return commands.provideGrant(session, grant)
  }

  override suspend fun provisionAppAuthKey(
    session: NfcSession,
    appAuthKey: ByteString,
  ) = retry { commands.provisionAppAuthKey(session, appAuthKey) }

  override suspend fun getConfirmationResult(
    session: NfcSession,
    handles: ConfirmationHandles,
  ): ConfirmationResult = retry { commands.getConfirmationResult(session, handles) }

  /**
   * Transforms a [HardwareInteraction] to ensure RequiresConfirmation uses retry-wrapped commands.
   *
   * When the underlying NfcCommands implementation returns a RequiresConfirmation, the callback
   * captures the unwrapped commands. This function wraps the fetchResult to ensure retry
   * logic is applied when it is invoked.
   */
  private fun <T> wrapHardwareInteraction(
    interaction: HardwareInteraction<T>,
  ): HardwareInteraction<T> {
    return when (interaction) {
      is HardwareInteraction.Completed -> interaction
      is HardwareInteraction.RequiresConfirmation -> {
        HardwareInteraction.RequiresConfirmation { session, commands ->
          val retryingCommands = RetryingNfcCommandsImpl(commands)
          retryingCommands.wrapHardwareInteraction(interaction.fetchResult(session, retryingCommands))
        }
      }
      is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
        HardwareInteraction.ConfirmWithEmulatedPrompt(
          options = interaction.options.map { option ->
            EmulatedPromptOption(
              name = option.name,
              fetchResult = { session, commands ->
                val retryingCommands = RetryingNfcCommandsImpl(commands)
                retryingCommands.wrapHardwareInteraction(option.fetchResult(session, retryingCommands))
              },
              onSelect = option.onSelect
            )
          }
        )
      }
    }
  }
}

private inline fun <T> retry(block: () -> T): T {
  for (retries in 1..MAX_NFC_COMMAND_RETRIES) {
    try {
      return block()
    } catch (e: CanBeRetried) {
      if (retries >= MAX_NFC_COMMAND_RETRIES) throw e
      logWarn(tag = "NFC", throwable = e) {
        "Retrying NFC command (retry $retries / $MAX_NFC_COMMAND_RETRIES)"
      }
    }
  }
  error("NFC retries overflowed; this shouldn't be possible!")
}
