package build.wallet.nfc.interceptors

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.SealedData
import build.wallet.crypto.SymmetricKey
import build.wallet.firmware.*
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.logging.logWarn
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcException.CanBeRetried
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.*
import build.wallet.nfc.platform.HardwareIdentityAwareNfcCommands
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.W3NfcCommands
import okio.ByteString

private const val MAX_NFC_COMMAND_RETRIES = 5
private const val IOS_TAG_RESPONSE_ERROR_MESSAGE = "Tag response error / no response"

/**
 * Retries NFC commands that are idempotent.
 */
internal fun retryCommands() =
  NfcTransactionInterceptor { next ->
    { session, commands ->
      val wrapped: NfcCommands = when (commands) {
        is W3NfcCommands -> RetryingW3NfcCommands(commands)
        else -> RetryingNfcCommands(commands)
      }
      next(session, wrapped)
    }
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
private open class RetryingNfcCommands(
  protected val commands: NfcCommands,
) : NfcCommands, HardwareIdentityAwareNfcCommands {
  override suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
    mcuRole: McuRole,
    version: String,
    deferCommit: Boolean,
  ): HardwareInteraction<Boolean> =
    wrapHardwareInteraction(
      retry {
        commands.fwupStart(session, patchSize, fwupMode, mcuRole, version, deferCommit)
      }
    )

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
      logWarn(
        tag = "NFC",
        throwable = e
      ) { "fwupFinish TransceiveFailure - mode: ${fwupMode.name}" }
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

  override suspend fun resolvedDeviceInfo(session: NfcSession): FirmwareDeviceInfo =
    retry {
      (commands as? HardwareIdentityAwareNfcCommands)?.resolvedDeviceInfo(session)
        ?: commands.getDeviceInfo(session)
    }

  override suspend fun getEvents(
    session: NfcSession,
    mcuRole: McuRole,
  ) = commands.getEvents(session, mcuRole)

  override suspend fun getFirmwareFeatureFlags(session: NfcSession): List<FirmwareFeatureFlagCfg> =
    retry { commands.getFirmwareFeatureFlags(session) }

  override suspend fun getFirmwareMetadata(
    session: NfcSession,
    mcuRole: McuRole,
  ) = retry { commands.getFirmwareMetadata(session, mcuRole) }

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

  override suspend fun getInitialSpendingPublicKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = retry { commands.getInitialSpendingPublicKey(session, network) }

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ) = retry { commands.getNextSpendingKey(session, existingDescriptorPublicKeys, network) }

  override suspend fun lockDevice(session: NfcSession) = retry { commands.lockDevice(session) }

  override suspend fun queryAuthentication(session: NfcSession) =
    retry { commands.queryAuthentication(session) }

  override suspend fun showConfirmationScreen(
    session: NfcSession,
    lockOnDismiss: Boolean,
  ) = retry { commands.showConfirmationScreen(session, lockOnDismiss) }

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
    displayPreference: HwDisplayPreference?,
    allowUnfinalized: Boolean,
  ): HardwareInteraction<Psbt> =
    wrapHardwareInteraction(
      retry {
        commands.signTransaction(session, psbt, spendingKeyset, displayPreference, allowUnfinalized)
      }
    )

  override suspend fun sweepTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
    sweepContext: SweepSigningContext,
    displayPreference: HwDisplayPreference?,
  ): HardwareInteraction<Psbt> =
    wrapHardwareInteraction(
      retry {
        commands.sweepTransaction(session, psbt, spendingKeyset, sweepContext, displayPreference)
      }
    )

  override suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = retry { commands.startFingerprintEnrollment(session, fingerprintHandle) }

  override suspend fun version(session: NfcSession) = retry { commands.version(session) }

  override suspend fun wipeDevice(session: NfcSession): HardwareInteraction<Boolean> =
    wrapHardwareInteraction(retry { commands.wipeDevice(session) })

  override suspend fun eekRestorationUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ): HardwareInteraction<SymmetricKey> =
    wrapHardwareInteraction(
      retry { commands.eekRestorationUnsealSymmetricKey(session, sealedKey) }
    )

  override suspend fun keysetRepairUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ): HardwareInteraction<SymmetricKey> =
    wrapHardwareInteraction(
      retry { commands.keysetRepairUnsealSymmetricKey(session, sealedKey) }
    )

  override suspend fun keysetRepairRotateHwKey(
    session: NfcSession,
    params: KeysetRepairRotateHwKeyParams,
  ): HardwareInteraction<KeysetRepairRotateHwKeyResult> =
    wrapHardwareInteraction(
      retry { commands.keysetRepairRotateHwKey(session, params) }
    )

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
   * Transforms a [HardwareInteraction] to ensure NFC callbacks use retry-wrapped commands.
   *
   * [HardwareInteraction.RequiresConfirmation] now carries pure data (handles + sync mapper)
   * rather than a suspend callback, so no wrapping is needed — the state machine calls
   * [NfcCommands.getConfirmationResult] directly, which is already covered by the
   * retry interceptor wrapping the commands at the session level.
   */
  protected fun <T> wrapHardwareInteraction(
    interaction: HardwareInteraction<T>,
  ): HardwareInteraction<T> {
    return when (interaction) {
      is HardwareInteraction.Completed -> interaction
      is HardwareInteraction.RequiresConfirmation -> {
        // handles and mapResult are pure data — no suspend closure to re-wrap.
        interaction
      }
      is HardwareInteraction.RequiresTransfer -> {
        HardwareInteraction.RequiresTransfer { session, commands, onProgress ->
          val retryingCommands = RetryingNfcCommands(commands)
          retryingCommands.wrapHardwareInteraction(
            interaction.transferAndFetch(session, retryingCommands, onProgress)
          )
        }
      }
      is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
        fun <T> wrapOption(option: EmulatedPromptOption<T>) =
          EmulatedPromptOption(
            fetchResult = { session, commands ->
              val retryingCommands = RetryingNfcCommands(commands)
              retryingCommands.wrapHardwareInteraction(option.fetchResult(session, retryingCommands))
            },
            onSelect = option.onSelect
          )
        HardwareInteraction.ConfirmWithEmulatedPrompt(
          details = interaction.details,
          approve = wrapOption(interaction.approve),
          deny = wrapOption(interaction.deny)
        )
      }
    }
  }
}

private class RetryingW3NfcCommands(
  private val w3Commands: W3NfcCommands,
) : RetryingNfcCommands(w3Commands), W3NfcCommands {
  override suspend fun signActionProof(
    session: NfcSession,
    version: UInt,
    action: ActionProofAction,
    value: String?,
    bindings: String,
  ): HardwareInteraction<String> =
    wrapHardwareInteraction(
      retry { w3Commands.signActionProof(session, version, action, value, bindings) }
    )

  override suspend fun lostAppRecovery(
    session: NfcSession,
    sealedSsek: ByteString,
    onSsekUnsealed: suspend (SymmetricKey) -> LostAppRecoveryContinueParams,
  ): HardwareInteraction<LostAppRecoveryCompositeResult> =
    wrapHardwareInteraction(
      retry { w3Commands.lostAppRecovery(session, sealedSsek, onSsekUnsealed) }
    )

  override suspend fun signChallengeAndSealSeks(
    session: NfcSession,
    challenge: ByteString,
    unsealedCsek: ByteString,
    unsealedSsek: ByteString,
  ): HardwareInteraction<SignChallengeAndSealSeksResult> =
    wrapHardwareInteraction(
      retry { w3Commands.signChallengeAndSealSeks(session, challenge, unsealedCsek, unsealedSsek) }
    )

  override suspend fun recoveryAuthorizeLostApp(
    session: NfcSession,
    sealedDdkData: SealedData?,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<RecoveryAuthorizeLostAppResult> =
    wrapHardwareInteraction(
      retry {
        w3Commands.recoveryAuthorizeLostApp(
          session,
          sealedDdkData,
          sealedSsekForDecryption,
          descriptorBackupsBindings,
          activateKeysetBindings,
          actionProofVersion
        )
      }
    )

  override suspend fun recoveryAuthorizeLostHw(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<RecoveryAuthorizeLostHwResult> =
    wrapHardwareInteraction(
      retry {
        w3Commands.recoveryAuthorizeLostHw(
          session,
          ddkPrivateKeyBytes,
          descriptorBackupsBindings,
          activateKeysetBindings,
          actionProofVersion
        )
      }
    )

  override suspend fun upgradeAuthorizeW3(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<UpgradeAuthorizeW3Result> =
    wrapHardwareInteraction(
      retry {
        w3Commands.upgradeAuthorizeW3(
          session,
          ddkPrivateKeyBytes,
          sealedSsekForDecryption,
          descriptorBackupsBindings,
          activateKeysetBindings,
          actionProofVersion
        )
      }
    )

  override suspend fun lostAppRecoverySignChallenge(
    session: NfcSession,
    challenge: ByteString,
  ): HardwareInteraction<String> =
    wrapHardwareInteraction(
      retry { w3Commands.lostAppRecoverySignChallenge(session, challenge) }
    )

  override suspend fun rotateAppAuthKeys(
    session: NfcSession,
    params: RotateAppAuthKeysContinueParams,
  ): HardwareInteraction<RotateAppAuthKeysCompositeResult> =
    wrapHardwareInteraction(
      retry { w3Commands.rotateAppAuthKeys(session, params) }
    )

  override suspend fun upgradeRotateAppAuthKeys(
    session: NfcSession,
    params: UpgradeRotateAppAuthKeysParams,
  ): HardwareInteraction<UpgradeRotateAppAuthKeysResult> =
    wrapHardwareInteraction(
      retry { w3Commands.upgradeRotateAppAuthKeys(session, params) }
    )

  override suspend fun <T> fullAccountCloudBackupRestoration(
    session: NfcSession,
    sealedCseks: List<SealedData>,
    onCsekUnsealed: suspend (CsekUnsealResult) -> T,
  ): HardwareInteraction<T> =
    wrapHardwareInteraction(
      retry { w3Commands.fullAccountCloudBackupRestoration(session, sealedCseks, onCsekUnsealed) }
    )

  override suspend fun getAddress(
    session: NfcSession,
    addressIndex: UInt,
  ): String = retry { w3Commands.getAddress(session, addressIndex) }

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
    retry {
      w3Commands.verifyKeysAndBuildDescriptor(
        session,
        appSpendingKey,
        appSpendingKeyChaincode,
        networkMainnet,
        appAuthKey,
        serverSpendingKey,
        serverSpendingKeyChaincode,
        wsmSignature,
        accountIndex
      )
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
