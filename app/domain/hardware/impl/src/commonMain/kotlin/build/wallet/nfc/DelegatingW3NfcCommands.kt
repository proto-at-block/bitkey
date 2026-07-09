package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.SealedData
import build.wallet.crypto.SymmetricKey
import build.wallet.firmware.*
import build.wallet.fwup.FwupMode
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.nfc.platform.*
import okio.ByteString

/**
 * Base class for session-aware W3 command wrappers that route each call to the correct backing
 * commands instance for the active NFC session.
 */
internal abstract class DelegatingW3NfcCommands : W3NfcCommands {
  protected abstract suspend fun delegatedCommands(session: NfcSession): NfcCommands

  protected open suspend fun delegatedW3Commands(session: NfcSession): W3NfcCommands =
    delegatedCommands(session) as? W3NfcCommands
      ?: throw NfcException.CommandError(
        message = "W3 commands required but got ${delegatedCommands(session)::class.simpleName}"
      )

  override suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
    mcuRole: McuRole,
    version: String,
    deferCommit: Boolean,
  ) = delegatedCommands(session).fwupStart(
    session = session,
    patchSize = patchSize,
    fwupMode = fwupMode,
    mcuRole = mcuRole,
    version = version,
    deferCommit = deferCommit
  )

  override suspend fun fwupTransfer(
    session: NfcSession,
    sequenceId: UInt,
    fwupData: List<UByte>,
    offset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole,
  ) = delegatedCommands(session).fwupTransfer(
    session = session,
    sequenceId = sequenceId,
    fwupData = fwupData,
    offset = offset,
    fwupMode = fwupMode,
    mcuRole = mcuRole
  )

  override suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole,
  ) = delegatedCommands(session).fwupFinish(
    session = session,
    appPropertiesOffset = appPropertiesOffset,
    signatureOffset = signatureOffset,
    fwupMode = fwupMode,
    mcuRole = mcuRole
  )

  override suspend fun getAuthenticationKey(session: NfcSession) =
    delegatedCommands(session).getAuthenticationKey(session)

  override suspend fun getCoredumpCount(session: NfcSession) =
    delegatedCommands(session).getCoredumpCount(session)

  override suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
    mcuRole: McuRole,
  ) = delegatedCommands(session).getCoredumpFragment(session, offset, mcuRole)

  override suspend fun getDeviceInfo(session: NfcSession) =
    delegatedCommands(session).getDeviceInfo(session)

  override suspend fun getEvents(
    session: NfcSession,
    mcuRole: McuRole,
  ) = delegatedCommands(session).getEvents(session, mcuRole)

  override suspend fun getFirmwareFeatureFlags(session: NfcSession) =
    delegatedCommands(session).getFirmwareFeatureFlags(session)

  override suspend fun getFingerprintEnrollmentStatus(
    session: NfcSession,
    isEnrollmentContextAware: Boolean,
  ) = delegatedCommands(session).getFingerprintEnrollmentStatus(session, isEnrollmentContextAware)

  override suspend fun deleteFingerprint(
    session: NfcSession,
    index: Int,
  ) = delegatedCommands(session).deleteFingerprint(session, index)

  override suspend fun getUnlockMethod(session: NfcSession) =
    delegatedCommands(session).getUnlockMethod(session)

  override suspend fun cancelFingerprintEnrollment(session: NfcSession) =
    delegatedCommands(session).cancelFingerprintEnrollment(session)

  override suspend fun getEnrolledFingerprints(session: NfcSession) =
    delegatedCommands(session).getEnrolledFingerprints(session)

  override suspend fun setFingerprintLabel(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = delegatedCommands(session).setFingerprintLabel(session, fingerprintHandle)

  override suspend fun getFirmwareMetadata(
    session: NfcSession,
    mcuRole: McuRole,
  ) = delegatedCommands(session).getFirmwareMetadata(session, mcuRole)

  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = delegatedCommands(session).getInitialSpendingKey(session, network)

  override suspend fun getInitialSpendingPublicKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = delegatedCommands(session).getInitialSpendingPublicKey(session, network)

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ) = delegatedCommands(session).getNextSpendingKey(
    session = session,
    existingDescriptorPublicKeys = existingDescriptorPublicKeys,
    network = network
  )

  override suspend fun lockDevice(session: NfcSession) =
    delegatedCommands(session).lockDevice(session)

  override suspend fun queryAuthentication(session: NfcSession) =
    delegatedCommands(session).queryAuthentication(session)

  override suspend fun showConfirmationScreen(
    session: NfcSession,
    lockOnDismiss: Boolean,
  ) = delegatedCommands(session).showConfirmationScreen(session, lockOnDismiss)

  override suspend fun sealData(
    session: NfcSession,
    unsealedData: ByteString,
  ) = delegatedCommands(session).sealData(session, unsealedData)

  override suspend fun unsealData(
    session: NfcSession,
    sealedData: SealedData,
  ) = delegatedCommands(session).unsealData(session, sealedData)

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ) = delegatedCommands(session).signChallenge(session, challenge)

  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
    displayPreference: HwDisplayPreference?,
    allowUnfinalized: Boolean,
  ) = delegatedCommands(session).signTransaction(
    session = session,
    psbt = psbt,
    spendingKeyset = spendingKeyset,
    displayPreference = displayPreference,
    allowUnfinalized = allowUnfinalized
  )

  override suspend fun sweepTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
    sweepContext: SweepSigningContext,
    displayPreference: HwDisplayPreference?,
  ) = delegatedCommands(session).sweepTransaction(
    session = session,
    psbt = psbt,
    spendingKeyset = spendingKeyset,
    sweepContext = sweepContext,
    displayPreference = displayPreference
  )

  override suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = delegatedCommands(session).startFingerprintEnrollment(session, fingerprintHandle)

  override suspend fun version(session: NfcSession) = delegatedCommands(session).version(session)

  override suspend fun wipeDevice(session: NfcSession) =
    delegatedCommands(session).wipeDevice(session)

  override suspend fun eekRestorationUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ) = delegatedCommands(session).eekRestorationUnsealSymmetricKey(session, sealedKey)

  override suspend fun keysetRepairUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ) = delegatedCommands(session).keysetRepairUnsealSymmetricKey(session, sealedKey)

  override suspend fun keysetRepairRotateHwKey(
    session: NfcSession,
    params: KeysetRepairRotateHwKeyParams,
  ) = delegatedCommands(session).keysetRepairRotateHwKey(session, params)

  override suspend fun getCert(
    session: NfcSession,
    certType: FirmwareCertType,
  ) = delegatedCommands(session).getCert(session, certType)

  override suspend fun signVerifyAttestationChallenge(
    session: NfcSession,
    deviceIdentityDer: List<UByte>,
    challenge: List<UByte>,
  ) = delegatedCommands(session).signVerifyAttestationChallenge(
    session = session,
    deviceIdentityDer = deviceIdentityDer,
    challenge = challenge
  )

  override suspend fun getGrantRequest(
    session: NfcSession,
    action: GrantAction,
  ) = delegatedCommands(session).getGrantRequest(session, action)

  override suspend fun provideGrant(
    session: NfcSession,
    grant: Grant,
  ) = delegatedCommands(session).provideGrant(session, grant)

  override suspend fun provisionAppAuthKey(
    session: NfcSession,
    appAuthKey: ByteString,
  ) = delegatedCommands(session).provisionAppAuthKey(session, appAuthKey)

  override suspend fun getConfirmationResult(
    session: NfcSession,
    handles: ConfirmationHandles,
  ) = delegatedCommands(session).getConfirmationResult(session, handles)

  override suspend fun signActionProof(
    session: NfcSession,
    version: UInt,
    action: ActionProofAction,
    value: String?,
    bindings: String,
  ) = delegatedW3Commands(session).signActionProof(
    session = session,
    version = version,
    action = action,
    value = value,
    bindings = bindings
  )

  override suspend fun lostAppRecovery(
    session: NfcSession,
    sealedSsek: ByteString,
    onSsekUnsealed: suspend (SymmetricKey) -> LostAppRecoveryContinueParams,
  ) = delegatedW3Commands(session).lostAppRecovery(session, sealedSsek, onSsekUnsealed)

  override suspend fun signChallengeAndSealSeks(
    session: NfcSession,
    challenge: ByteString,
    unsealedCsek: ByteString,
    unsealedSsek: ByteString,
  ) = delegatedW3Commands(session).signChallengeAndSealSeks(
    session = session,
    challenge = challenge,
    unsealedCsek = unsealedCsek,
    unsealedSsek = unsealedSsek
  )

  override suspend fun recoveryAuthorizeLostApp(
    session: NfcSession,
    sealedDdkData: SealedData?,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ) = delegatedW3Commands(session).recoveryAuthorizeLostApp(
    session = session,
    sealedDdkData = sealedDdkData,
    sealedSsekForDecryption = sealedSsekForDecryption,
    descriptorBackupsBindings = descriptorBackupsBindings,
    activateKeysetBindings = activateKeysetBindings,
    actionProofVersion = actionProofVersion
  )

  override suspend fun recoveryAuthorizeLostHw(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ) = delegatedW3Commands(session).recoveryAuthorizeLostHw(
    session = session,
    ddkPrivateKeyBytes = ddkPrivateKeyBytes,
    descriptorBackupsBindings = descriptorBackupsBindings,
    activateKeysetBindings = activateKeysetBindings,
    actionProofVersion = actionProofVersion
  )

  override suspend fun upgradeAuthorizeW3(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ) = delegatedW3Commands(session).upgradeAuthorizeW3(
    session = session,
    ddkPrivateKeyBytes = ddkPrivateKeyBytes,
    sealedSsekForDecryption = sealedSsekForDecryption,
    descriptorBackupsBindings = descriptorBackupsBindings,
    activateKeysetBindings = activateKeysetBindings,
    actionProofVersion = actionProofVersion
  )

  override suspend fun lostAppRecoverySignChallenge(
    session: NfcSession,
    challenge: ByteString,
  ) = delegatedW3Commands(session).lostAppRecoverySignChallenge(session, challenge)

  override suspend fun rotateAppAuthKeys(
    session: NfcSession,
    params: RotateAppAuthKeysContinueParams,
  ) = delegatedW3Commands(session).rotateAppAuthKeys(session, params)

  override suspend fun upgradeRotateAppAuthKeys(
    session: NfcSession,
    params: UpgradeRotateAppAuthKeysParams,
  ) = delegatedW3Commands(session).upgradeRotateAppAuthKeys(session, params)

  override suspend fun <T> fullAccountCloudBackupRestoration(
    session: NfcSession,
    sealedCseks: List<SealedData>,
    onCsekUnsealed: suspend (CsekUnsealResult) -> T,
  ) = delegatedW3Commands(session).fullAccountCloudBackupRestoration(
    session = session,
    sealedCseks = sealedCseks,
    onCsekUnsealed = onCsekUnsealed
  )

  override suspend fun getAddress(
    session: NfcSession,
    addressIndex: UInt,
  ) = delegatedW3Commands(session).getAddress(session, addressIndex)

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
  ) = delegatedW3Commands(session).verifyKeysAndBuildDescriptor(
    session = session,
    appSpendingKey = appSpendingKey,
    appSpendingKeyChaincode = appSpendingKeyChaincode,
    networkMainnet = networkMainnet,
    appAuthKey = appAuthKey,
    serverSpendingKey = serverSpendingKey,
    serverSpendingKeyChaincode = serverSpendingKeyChaincode,
    wsmSignature = wsmSignature,
    accountIndex = accountIndex
  )
}
