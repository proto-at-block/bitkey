package build.wallet.nfc.platform

import bitkey.account.HardwareType
import bitkey.auth.AccessToken
import bitkey.data.PrivateData
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SealedData
import build.wallet.crypto.SymmetricKey
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.firmware.*
import build.wallet.firmware.EnrolledFingerprints.Companion.FIRST_FINGERPRINT_INDEX
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * Result of a command that required user confirmation on the device.
 */
sealed interface ConfirmationResult {
  data class WipeDevice(val success: Boolean) : ConfirmationResult

  data class FwupStart(val success: Boolean) : ConfirmationResult

  /**
   * Result of a confirmed action proof signing operation.
   */
  data class SignActionProof(val signature: String) : ConfirmationResult

  /**
   * Per-input signature produced by the hardware for a non-PSBT signing flow.
   */
  data class InputSignature(
    /** Zero-based index into the original inputs array. */
    val inputIndex: UInt,
    /** Compressed public key that produced this signature (33 bytes). */
    val publicKey: List<UByte>,
    /** DER-encoded ECDSA signature + sighash type byte. */
    val signature: List<UByte>,
  )

  /**
   * Result of a confirmed transaction signing operation (non-PSBT flow).
   */
  data class SignTx(val signatures: List<InputSignature>) : ConfirmationResult

  /**
   * Result of a confirmed streaming transaction signing operation.
   * Indicates the firmware is ready for per-input signature retrieval via
   * `get_tx_signature_cmd`. The app should iterate input indices 0..numInputs-1.
   */
  data class SignStreamReady(val numInputs: UInt) : ConfirmationResult

  /**
   * Unsealed SSEK returned after user confirms lost app recovery on the device.
   * The app uses this to decrypt descriptor backups and proceed with recovery.
   */
  data class LostAppRecoverySsek(val ssek: List<UByte>) : ConfirmationResult

  /**
   * Signature returned after user confirms lost app recovery sign challenge on the device.
   */
  data class LostAppRecoverySignChallenge(val signature: String) : ConfirmationResult

  /**
   * Result of rotate app auth keys composite command after user confirms on device.
   * Contains all signatures needed for the auth key rotation request.
   */
  data class RotateAppAuthKeys(
    val actionProofSignature: List<UByte>,
    val hwSignedAccountId: List<UByte>,
    val appAuthKeySignature: List<UByte>,
    val hwAuthPublicKey: List<UByte>,
  ) : ConfirmationResult

  /**
   * Result of upgrade rotate app auth keys composite command after user confirms on device.
   * Like [RotateAppAuthKeys] but without action proof signature.
   */
  data class UpgradeRotateAppAuthKeys(
    val hwSignedAccountId: List<UByte>,
    val appAuthKeySignature: List<UByte>,
    val hwAuthPublicKey: List<UByte>,
  ) : ConfirmationResult

  /**
   * Result of the signChallengeAndSealSeks confirmable command.
   * Contains the signed challenge and sealed CSEK/SSEK.
   */
  data class SignChallengeAndSealSeks(
    val signature: List<UByte>,
    val sealedCsek: List<UByte>,
    val sealedSsek: List<UByte>,
  ) : ConfirmationResult

  /**
   * Result of the recoveryAuthorizeLostApp confirmable command.
   * Contains SAP signatures, unsealed DDK/SSEK, and spending key info.
   */
  data class RecoveryAuthorizeLostApp(
    val descriptorBackupsSignature: List<UByte>,
    val activateKeysetSignature: List<UByte>,
    val unsealedDdkData: List<UByte>,
    val unsealedSsek: List<UByte>,
  ) : ConfirmationResult

  /**
   * Result of the recoveryAuthorizeLostHw confirmable command.
   * Contains SAP signatures, sealed DDK, and spending key info.
   */
  data class RecoveryAuthorizeLostHw(
    val descriptorBackupsSignature: List<UByte>,
    val activateKeysetSignature: List<UByte>,
    val sealedDdkData: List<UByte>,
  ) : ConfirmationResult

  /**
   * Result of the upgradeAuthorizeW3 confirmable command.
   * Contains SAP signatures, sealed DDK, and optionally an unsealed prior SSEK from the
   * W3 upgrade composite tap.
   */
  data class UpgradeAuthorizeW3(
    val descriptorBackupsSignature: List<UByte>,
    val activateKeysetSignature: List<UByte>,
    val sealedDdkData: List<UByte>,
    val unsealedSsek: List<UByte>?,
  ) : ConfirmationResult

  /**
   * Unsealed symmetric key returned after user confirms EEK restoration on the device.
   */
  data class EekRestorationUnsealSymmetricKey(val unsealedKey: List<UByte>) : ConfirmationResult

  /**
   * User confirmed full account cloud backup restoration on the device.
   * The session is now ready for continuation commands with sealed CSEKs.
   */
  data object FullAccountCloudBackupRestoration : ConfirmationResult

  /**
   * User has not yet approved or denied the confirmation on the device.
   * The app should show a screen prompting the user to make a decision.
   */
  data object Pending : ConfirmationResult

  /**
   * User explicitly denied the operation on the device.
   */
  data object Denied : ConfirmationResult
}

/**
 * Handles for retrieving the result of a confirmed command.
 */
data class ConfirmationHandles(
  val responseHandle: List<UByte>,
  val confirmationHandle: List<UByte>,
)

/**
 * Result of the lost app recovery composite command.
 *
 * Contains all outputs from the two-phase composite: the action proof signature
 * from the firmware, the new hardware spending key, and the app auth key signature.
 */
data class LostAppRecoveryCompositeResult(
  /** Hex-encoded action proof signature from the hardware (matches signActionProof convention). */
  val actionProofSignature: String,
  /** The new hardware spending key as a dpub descriptor. */
  val spendingKeyDpub: DescriptorPublicKey,
  /** Hex-encoded signature over the app auth key. */
  val appAuthKeySignature: String,
)

/**
 * Result of the rotate app auth keys composite command.
 *
 * Contains the action proof signature, the HW-signed account ID, the
 * HW-signed app global auth key and the hw auth public key
 */
data class RotateAppAuthKeysCompositeResult(
  /** Hex-encoded action proof signature from the hardware. */
  val actionProofSignature: String,
  /** Hex-encoded signature over the account ID. */
  val hwSignedAccountId: String,
  /** Hex-encoded signature over the app global auth public key. */
  val appGlobalAuthKeyHwSignature: String,
  /** The hardware authentication public key, retrieved post-confirmation. */
  val hwAuthPublicKey: HwAuthPublicKey,
)

/**
 * Parameters for the rotate app auth keys composite command,
 * provided by the caller with action proof bindings and the values to sign.
 */
data class RotateAppAuthKeysContinueParams(
  val actionProofVersion: UInt,
  val actionProofAction: ActionProofAction,
  val actionProofBindings: String,
  val accountId: String,
  val appGlobalAuthPublicKey: String,
)

/**
 * Result of the upgrade rotate app auth keys composite command (W3 upgrade flow).
 *
 * Like [RotateAppAuthKeysCompositeResult] but without the action proof signature.
 * Contains the HW-signed account ID, HW-signed app auth key, and the HW auth public key.
 */
data class UpgradeRotateAppAuthKeysResult(
  /** Hex-encoded signature over the account ID. */
  val hwSignedAccountId: String,
  /** Hex-encoded signature over the app global auth public key. */
  val appGlobalAuthKeyHwSignature: String,
  /** The hardware authentication public key, retrieved post-confirmation. */
  val hwAuthPublicKey: HwAuthPublicKey,
)

/**
 * Parameters for the upgrade rotate app auth keys command (W3 upgrade flow).
 * Only requires account ID and app auth key -- no action proof fields.
 */
data class UpgradeRotateAppAuthKeysParams(
  val accountId: String,
  val appGlobalAuthPublicKey: String,
)

/**
 * Parameters for the continue phase of lost app recovery, provided by the caller
 * after decrypting the SSEK and extracting descriptor information.
 */
data class LostAppRecoveryContinueParams(
  val actionProofVersion: UInt,
  val actionProofAction: ActionProofAction,
  val actionProofBindings: String,
  val existingHwSpendingKeys: List<HwSpendingPublicKey>,
  val network: BitcoinNetworkType,
  val appGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
)

/**
 * Result of a successful CSEK unseal during full account cloud backup restoration.
 *
 * @param index the zero-based index into the [sealedCseks] list that was successfully unsealed
 * @param unsealedCsek the unsealed symmetric key
 */
data class CsekUnsealResult(
  val index: Int,
  val unsealedCsek: SymmetricKey,
)

/**
 * Result of the W3 sign challenge and seal SEKs composite command (tap 1).
 *
 * Contains the signed challenge, sealed CSEK, and sealed SSEK produced by the hardware.
 */
data class SignChallengeAndSealSeksResult(
  val signedChallenge: String,
  val sealedCsek: SealedData,
  val sealedSsek: SealedData,
)

/**
 * Result of W3 recovery authorize lost app command (tap 2 for lost app recovery).
 *
 * Contains two hw-signed action proof signatures (hex strings) — one for descriptor backup
 * upload and one for keyset activation.
 * Also returns unsealed DDK data and SSEK bytes when the hardware decrypts sealed material.
 */
data class RecoveryAuthorizeLostAppResult(
  val descriptorBackupsSignature: String,
  val activateKeysetSignature: String,
  val unsealedDdkData: ByteString?,
  val unsealedSsek: ByteString?,
)

/**
 * Result of W3 recovery authorize lost hw command (tap 2 for lost hardware recovery).
 *
 * Contains two hw-signed action proof signatures (hex strings) — one for descriptor backup
 * upload and one for keyset activation.
 * Also returns sealed DDK data when the hardware encrypts the provided private key.
 */
data class RecoveryAuthorizeLostHwResult(
  val descriptorBackupsSignature: String,
  val activateKeysetSignature: String,
  val sealedDdkData: SealedData?,
)

/**
 * Result of the W3 upgrade composite command. Contains two SAP action proof signatures,
 * sealed DDK data, and optionally an unsealed prior SSEK for resumed descriptor recovery.
 */
data class UpgradeAuthorizeW3Result(
  val descriptorBackupsSignature: String,
  val activateKeysetSignature: String,
  val sealedDdkData: SealedData,
  val unsealedSsek: ByteString? = null,
)

/**
 * Display preferences sent to the hardware device during transaction signing.
 * The hardware uses these to format amounts on its screen during transaction
 * confirmation. These are ephemeral — the hardware does not persist them.
 */
data class HwDisplayPreference(
  /** Whether to display Bitcoin amounts in satoshi or BTC. */
  val bitcoinDisplayUnit: BitcoinDisplayUnit,
)

/**
 * [NfcCommands] has a method for each primitive (core) NFC command (e.g. version or sealKey),
 * each taking an [NfcSession] as well as their respective arguments.
 */

@Suppress("TooManyFunctions")
interface NfcCommands {
  /**
   * Start FWUP process for a specific MCU.
   *
   * For W3 hardware, this may return [HardwareInteraction.RequiresConfirmation] which requires
   * the user to confirm on the device before completing the operation.
   *
   * @param mcuRole Target MCU (defaults to CORE for W1 compatibility)
   * @param version Version string of the firmware being transferred (e.g. "1.2.3").
   *   Firmware will display this and verify it matches the signed metadata after transfer.
   * @param deferCommit When true, firmware defers committing the verified signature to flash,
   *   enabling atomic multi-MCU updates where both UXC and Core verify first and then commit
   *   together. Should be set to true for UXC when both MCUs need updating. Old firmware
   *   ignores this field (proto3 default false preserves legacy behavior).
   */
  suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
    mcuRole: McuRole = McuRole.CORE,
    version: String,
    deferCommit: Boolean = false,
  ): HardwareInteraction<Boolean>

  /**
   * Incremental transfer for FWUP.
   * @param mcuRole Target MCU (defaults to CORE for W1 compatibility)
   */
  suspend fun fwupTransfer(
    session: NfcSession,
    sequenceId: UInt,
    fwupData: List<UByte>,
    offset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole = McuRole.CORE,
  ): Boolean

  /**
   * Finish FWUP process.
   * @param mcuRole Target MCU (defaults to CORE for W1 compatibility)
   */
  suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole = McuRole.CORE,
  ): FwupFinishResponseStatus

  /**
   * Retrieve authentication public key from the hardware.
   * This public key is constant.
   */
  suspend fun getAuthenticationKey(session: NfcSession): HwAuthPublicKey

  /**
   * Get a count of coredump fragments for firmware telemetry.
   */
  suspend fun getCoredumpCount(session: NfcSession): Int

  /**
   * Get coredump at the given offset for firmware telemetry.
   */
  suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
    mcuRole: McuRole,
  ): CoredumpFragment

  /**
   * Get device info for the firmware on the hardware device.
   */
  suspend fun getDeviceInfo(session: NfcSession): FirmwareDeviceInfo

  /**
   * Get events for firmware telemetry.
   */
  suspend fun getEvents(
    session: NfcSession,
    mcuRole: McuRole,
  ): EventFragment

  /**
   * Get firmware feature flags.
   */
  suspend fun getFirmwareFeatureFlags(session: NfcSession): List<FirmwareFeatureFlagCfg>

  /**
   * Get the current fingerprint enrollment status for the hardware device, i.e. whether
   * enrollment is complete or requires additional fingerprints. The FingerprintEnrollmentResult
   * includes diagnostics accumulated during the enrollment process, as well as the final status.
   *
   * This command's behavior changed when multiple fingerprints were introduced. Originally, it
   * would always return [FingerprintEnrollmentStatus.COMPLETE] if any fingerprints were enrolled.
   * It was updated to be aware of a given enrollment context, meaning after an enrollment is
   * completed and the device eventually resets, it will return [FingerprintEnrollmentStatus.NOT_IN_PROGRESS]
   * even if there is an enrolled fingerprint. To allow backwards compatibility, we've introduced
   * [isEnrollmentContextAware], which when set to false will use the old behavior (e.g. for
   * initial onboarding), and when true will use the new behavior. See W-8306 for more details and
   * for a more robust fix.
   */
  suspend fun getFingerprintEnrollmentStatus(
    session: NfcSession,
    isEnrollmentContextAware: Boolean = false,
  ): FingerprintEnrollmentResult

  /**
   * Removes the fingerprint enrolled for the given [index]. Attempting to remove the
   * last remaining fingerprint will fail.
   */
  suspend fun deleteFingerprint(
    session: NfcSession,
    index: Int,
  ): Boolean

  /**
   * Returns the method that most recently unlocked the hardware device.
   */
  suspend fun getUnlockMethod(session: NfcSession): UnlockInfo

  /**
   * Cancels an ongoing fingerprint enrollment; e.g. if [getFingerprintEnrollmentStatus] returned
   * [FingerprintEnrollmentStatus.INCOMPLETE].
   *
   * This can be called safely even if no enrollment is in progress.
   */
  suspend fun cancelFingerprintEnrollment(session: NfcSession): Boolean

  /**
   * Get all enrolled fingerprints for the hardware device.
   */
  suspend fun getEnrolledFingerprints(session: NfcSession): EnrolledFingerprints

  /**
   * Sets the [FingerprintHandle.label] for an existing fingerprint.
   */
  suspend fun setFingerprintLabel(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ): Boolean

  /**
   * Get metadata for the firmware on the hardware device.
   *
   * This command is larger and slower than [getDeviceInfo] and should only be used for debug
   * purposes.
   *
   * @param mcuRole Target MCU to query metadata for (defaults to CORE for W1 compatibility)
   */
  suspend fun getFirmwareMetadata(
    session: NfcSession,
    mcuRole: McuRole = McuRole.CORE,
  ): FirmwareMetadata

  /**
   * Return a new and unique initial spending key.
   *
   * @param network the network for which the spend key will be used
   */
  suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ): HwSpendingPublicKey

  /**
   * Return the next unique spending key based on a set of existing spending keys
   *
   * @param existingDescriptorPublicKeys - the existing spending public keys used by the client
   * @param network - the network for which the spending key will be used
   */
  suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ): HwSpendingPublicKey

  /**
   * Lock the device after use is complete.
   */
  suspend fun lockDevice(session: NfcSession): Boolean

  /**
   * Query the authentication state of the HW (i.e. whether it is currently unlocked or not).
   */
  suspend fun queryAuthentication(session: NfcSession): Boolean

  /**
   * Show a "Success" confirmation screen on the hardware device (checkmark + "Success").
   * Used after operations like hardware presence checks to give the user visual feedback
   * that the device responded.
   *
   * @param lockOnDismiss If true, the device locks after the confirmation screen times out.
   */
  suspend fun showConfirmationScreen(
    session: NfcSession,
    lockOnDismiss: Boolean = false,
  ): Boolean

  /**
   * Encrypt a chunk of data, typically another key, using the hardware key.
   *
   * @param unsealedData a 32-byte key or other data to be encrypted.
   */
  suspend fun sealData(
    session: NfcSession,
    unsealedData: ByteString,
  ): SealedData

  /**
   * Decrypt a chunk of data using the hardware symmetric key.
   */
  suspend fun unsealData(
    session: NfcSession,
    sealedData: SealedData,
  ): ByteString

  /**
   * Sign a challenge (e.g. an auth challenge returned from f8e, an access token for
   * proof of possession, or static strings required by server endpoints).
   *
   * @param challenge - The challenge to sign.
   */
  suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ): String

  /**
   * Sign the given [psbt] with the hardware private spending key.
   *
   * @param spendingKeyset: The keyset associated with the PSBT. Only used when signing
   * multiple PSBTs during integration testing. TODO (W-4650): Remove from public API
   * @param displayPreference: Display preferences for the hardware screen during transaction
   * confirmation. When provided, the hardware uses these to format amounts. Not persisted.
   * Only used by W3 hardware (W1 ignores this parameter).
   *
   * @return A PSBT with the hardware signature.
   */
  suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
    displayPreference: HwDisplayPreference? = null,
  ): HardwareInteraction<Psbt>

  /**
   * Start fingerprint enrollment at the index specified by [fingerprintHandle].
   *
   * Defaults to the 0 index, with no label. Up to 3 fingerprints are supported.
   *
   * @param fingerprintHandle: The index and label associated with the fingerprint to enroll.
   * Starting enrollment for an index that already contains an enrolled fingerprint will overwrite
   * that fingerprint.
   */
  suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle = FingerprintHandle(
      FIRST_FINGERPRINT_INDEX,
      ""
    ),
  ): Boolean

  /**
   * Get the current version of the hardware device.
   */
  suspend fun version(session: NfcSession): UShort

  /**
   * Wipe the keys on the hardware device.
   *
   * This command may require user confirmation on the device. When confirmation is required,
   * returns [HardwareInteraction.RequiresConfirmation] which should be used to retrieve the result
   * after the user confirms on the device.
   */
  suspend fun wipeDevice(session: NfcSession): HardwareInteraction<Boolean>

  /**
   * Sign an action proof for privileged operations using the Two-Tap Hardware Confirmation Protocol.
   *
   * This command may require user confirmation on the device. When confirmation is required,
   * returns [HardwareInteraction.RequiresConfirmation] which should be used to retrieve the result
   * after the user confirms on the device.
   *
   * @param session the active `NfcSession` used to communicate with the hardware device
   * @param version protocol version (currently 1)
   * @param action the action for the proof
   * @param value new value being set (nullable)
   * @param bindings pre-sorted comma-joined context bindings (e.g., "eid=ABC,tb=59dc...")
   * @return hex-encoded 65-byte recoverable ECDSA signature
   */
  suspend fun signActionProof(
    session: NfcSession,
    version: UInt,
    action: ActionProofAction,
    value: String?,
    bindings: String,
  ): HardwareInteraction<String>

  /**
   * Initiates the lost app recovery composite command on W3 hardware.
   *
   * This is a two-tap confirmable action:
   * 1. First tap: Sends sealed SSEK → firmware shows "Confirm Lost App Recovery?" prompt
   * 2. Second tap (after user confirms): Retrieves unsealed SSEK, calls [onSsekUnsealed]
   *    to decrypt descriptors and build continue params, then sends the continue command
   *    with action proof fields and gets back signatures + spending key.
   *
   * @param session the active NFC session
   * @param sealedSsek protobuf-encoded sealed SSEK bytes
   * @param onSsekUnsealed callback invoked with the unsealed SSEK after user confirms;
   *   must return the [LostAppRecoveryContinueParams] for the continue phase
   * @return HardwareInteraction that resolves to [LostAppRecoveryCompositeResult]
   */
  suspend fun lostAppRecovery(
    session: NfcSession,
    sealedSsek: ByteString,
    onSsekUnsealed: suspend (SymmetricKey) -> LostAppRecoveryContinueParams,
  ): HardwareInteraction<LostAppRecoveryCompositeResult>

  /**
   * W3 completing recovery tap 1: signs the D&N challenge and seals the provided CSEK + SSEK.
   *
   * The caller generates the raw CSEK and SSEK locally, passes them here for hardware sealing,
   * and persists both the raw and sealed forms so later flows (descriptor encryption, cloud
   * backup) can look up the raw key by its sealed counterpart.
   *
   * This is a confirmable command — the hardware shows a prompt and the user must
   * confirm on the device before the result is available.
   *
   * @param session the active NFC session
   * @param challenge the delay-notify challenge to sign
   * @param unsealedCsek raw CSEK bytes to seal
   * @param unsealedSsek raw SSEK bytes to seal
   * @return HardwareInteraction that resolves to [SignChallengeAndSealSeksResult]
   */
  suspend fun signChallengeAndSealSeks(
    session: NfcSession,
    challenge: ByteString,
    unsealedCsek: ByteString,
    unsealedSsek: ByteString,
  ): HardwareInteraction<SignChallengeAndSealSeksResult>

  /**
   * W3 completing recovery tap 2 for lost app: signs action proofs and unseals DDK/SSEK material.
   *
   * This is a confirmable command — the hardware shows a prompt and the user must
   * confirm on the device before the result is available.
   *
   * @param session the active NFC session
   * @param sealedDdkData sealed DDK data to unseal (null if no DDK)
   * @param sealedSsekForDecryption sealed SSEK for decryption (null if not needed)
   * @param descriptorBackupsBindings pre-sorted comma-joined bindings for descriptor backups proof
   * @param activateKeysetBindings pre-sorted comma-joined bindings for keyset activation proof
   * @return HardwareInteraction that resolves to [RecoveryAuthorizeLostAppResult]
   */
  suspend fun recoveryAuthorizeLostApp(
    session: NfcSession,
    sealedDdkData: SealedData?,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<RecoveryAuthorizeLostAppResult>

  /**
   * W3 completing recovery tap 2 for lost hw: signs action proofs and seals the provided DDK private key.
   *
   * This is a confirmable command — the hardware shows a prompt and the user must
   * confirm on the device before the result is available.
   *
   * @param session the active NFC session
   * @param ddkPrivateKeyBytes DDK private key bytes to seal (null if no DDK)
   * @param descriptorBackupsBindings pre-sorted comma-joined bindings for descriptor backups proof
   * @param activateKeysetBindings pre-sorted comma-joined bindings for keyset activation proof
   * @return HardwareInteraction that resolves to [RecoveryAuthorizeLostHwResult]
   */
  suspend fun recoveryAuthorizeLostHw(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<RecoveryAuthorizeLostHwResult>

  /**
   * Composite command for W3 upgrade: signs both descriptor-backup and keyset-activation
   * action proofs, seals the DDK private key, and optionally unseals a prior descriptor-backup
   * SSEK, all in a single confirmable tap.
   *
   * Firmware shows "Approve wallet upgrade" prompt. After user confirms on device, the result
   * (sealed DDK + SAP signatures) is returned.
   *
   * @param session the active NFC session
   * @param ddkPrivateKeyBytes DDK private key bytes to seal
   * @param sealedSsekForDecryption sealed SSEK for decrypting prior descriptor backups
   * @param descriptorBackupsBindings pre-sorted comma-joined bindings for descriptor backups proof
   * @param activateKeysetBindings pre-sorted comma-joined bindings for keyset activation proof
   * @return HardwareInteraction that resolves to [UpgradeAuthorizeW3Result]
   */
  suspend fun upgradeAuthorizeW3(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<UpgradeAuthorizeW3Result>

  /**
   * Signs the auth challenge during lost app recovery with on-device confirmation.
   *
   * For W3 hardware, this is a confirmable command that shows "Confirm this is your account?"
   * on the device screen before signing. W1 firmware does not support this command.
   *
   * @param session the active NFC session
   * @param challenge the auth challenge bytes to sign
   * @return HardwareInteraction that resolves to a DER-encoded hex signature string,
   *   matching the format returned by [signChallenge]
   */
  suspend fun lostAppRecoverySignChallenge(
    session: NfcSession,
    challenge: ByteString,
  ): HardwareInteraction<String>

  /**
   * Composite W3 command for rotating app auth keys.
   *
   * Performs a confirmable `signActionProof(ROTATE_APP_AUTH_KEYS)`, then after user confirmation
   * executes `signChallenge(accountId)`, `signChallenge(appGlobalAuthPublicKey)`, and
   * `getAuthenticationKey()` in the same NFC session.
   *
   * @param session the active NFC session
   * @param params parameters containing action proof bindings and values to sign
   * @return HardwareInteraction that resolves to [RotateAppAuthKeysCompositeResult]
   */
  suspend fun rotateAppAuthKeys(
    session: NfcSession,
    params: RotateAppAuthKeysContinueParams,
  ): HardwareInteraction<RotateAppAuthKeysCompositeResult>

  /**
   * Confirmable composite command for W3 upgrade auth key rotation without action proof signing.
   *
   * Like [rotateAppAuthKeys] but skips the action proof signature. After user confirms on device,
   * signs `accountId` and `appGlobalAuthPublicKey` with the HW auth key, and returns the
   * HW auth public key.
   *
   * @param session the active NFC session
   * @param params parameters containing account ID and app auth public key
   * @return HardwareInteraction that resolves to [UpgradeRotateAppAuthKeysResult]
   */
  suspend fun upgradeRotateAppAuthKeys(
    session: NfcSession,
    params: UpgradeRotateAppAuthKeysParams,
  ): HardwareInteraction<UpgradeRotateAppAuthKeysResult>

  /**
   * Unseal a symmetric key during EEK (Emergency Exit Kit) restoration with user confirmation.
   *
   * This is a confirmable command on W3 hardware:
   * 1. First tap: sends sealed key → firmware shows confirmation prompt → returns RequiresConfirmation
   * 2. Second tap (after user confirms): retrieves unsealed key via getConfirmationResult
   *
   * On W1 hardware, this delegates to [unsealData] without requiring confirmation.
   *
   * @param session the active NFC session
   * @param sealedKey the sealed symmetric key to unseal
   * @return HardwareInteraction that resolves to the unsealed SymmetricKey
   */
  suspend fun eekRestorationUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ): HardwareInteraction<SymmetricKey>

  /**
   * Full account cloud backup restoration with user confirmation on W3 hardware.
   *
   * This is a confirmable command with a two-tap streaming protocol:
   * 1. First tap: firmware shows "Decrypt your wallet backups?" → returns RequiresConfirmation
   * 2. Second tap (after user confirms): streams sealed CSEKs to firmware one at a time.
   *    Firmware attempts to unseal each CSEK. When one succeeds, firmware returns the
   *    unsealed key along with its index. The [onCsekUnsealed] callback is then invoked
   *    within the same NFC session to allow further operations (e.g. device info sync).
   *
   * This is a W3-only command. On W1, callers should use [unsealSymmetricKey] directly.
   *
   * @param session the active NFC session
   * @param sealedCseks the sealed CSEKs to stream to firmware for unsealing
   * @param onCsekUnsealed callback invoked with the [CsekUnsealResult] (index + unsealed key)
   *   after firmware successfully unseals one. Runs within the same NFC session so the
   *   caller can issue additional NFC commands (e.g. getDeviceInfo, getEnrolledFingerprints).
   *   Must return the final result for the interaction.
   * @return HardwareInteraction that resolves to the result of [onCsekUnsealed]
   */
  suspend fun <T> fullAccountCloudBackupRestoration(
    session: NfcSession,
    sealedCseks: List<SealedData>,
    onCsekUnsealed: suspend (CsekUnsealResult) -> T,
  ): HardwareInteraction<T>

  /**
   * Get the certificate for the hardware device.
   */
  suspend fun getCert(
    session: NfcSession,
    certType: FirmwareCertType,
  ): List<UByte>

  suspend fun signVerifyAttestationChallenge(
    session: NfcSession,
    deviceIdentityDer: List<UByte>,
    challenge: List<UByte>,
  ): Boolean

  /**
   * Retrieves a GrantRequest from the hardware for a specified action.
   *
   * @param session The NFC session.
   * @param action The specific action for which the grant is being requested.
   * @return The [GrantRequest] generated by the firmware.
   */
  suspend fun getGrantRequest(
    session: NfcSession,
    action: GrantAction,
  ): GrantRequest

  /**
   * Provides a server-signed Grant to the hardware.
   *
   * @param session The NFC session.
   * @param grant The server-signed [Grant] object.
   * @return True if the grant was successfully provided and processed by the firmware, false otherwise.
   */
  suspend fun provideGrant(
    session: NfcSession,
    grant: Grant,
  ): Boolean

  /**
   * Provisions the app authentication public key on the hardware for future authentication flows.
   *
   * @param session the active `NfcSession` used to communicate with the hardware device
   * @param appAuthKey the app's global authentication public key to be stored on the hardware
   * @return true if the key was stored successfully, false otherwise
   */
  suspend fun provisionAppAuthKey(
    session: NfcSession,
    appAuthKey: ByteString,
  ): Boolean

  /**
   * Retrieves the result of a command that required user confirmation on the device.
   *
   * @param session the active `NfcSession` used to communicate with the hardware device
   * @param handles the handles returned from the initial command that required confirmation
   * @return the result of the confirmed command
   */
  suspend fun getConfirmationResult(
    session: NfcSession,
    handles: ConfirmationHandles,
  ): ConfirmationResult

  /**
   * Generate and display a bitcoin address on the hardware device.
   *
   * The hardware derives the address from its stored descriptor at the given index
   * and displays it on screen for user verification.
   *
   * @param session the active `NfcSession` used to communicate with the hardware device
   * @param addressIndex the address index for derivation (0, 1, 2, etc.)
   * @return the derived address string
   */
  suspend fun getAddress(
    session: NfcSession,
    addressIndex: UInt,
  ): String

  /**
   * Verifies app spending key, app auth key, and server spending key on W3 hardware,
   * and builds the wallet descriptor.
   *
   * This command is only available on W3 devices (not W1).
   *
   * @param session the active `NfcSession` used to communicate with the hardware device
   * @param appSpendingKey 33-byte compressed secp256k1 public key for app spending
   * @param appSpendingKeyChaincode 32-byte chaincode for app spending key
   * @param networkMainnet true for mainnet, false for testnet
   * @param appAuthKey 33-byte compressed secp256k1 public key for app authentication
   * @param serverSpendingKey 33-byte compressed secp256k1 public key for server spending
   * @param serverSpendingKeyChaincode 32-byte chaincode for server spending key
   * @param wsmSignature 64-byte compact ECDSA signature from WSM
   * @param accountIndex BIP84 account index for key derivation (defaults to 0)
   * @return the HW signature over the app auth key (AppGlobalAuthKeyHwSignature)
   */
  suspend fun verifyKeysAndBuildDescriptor(
    session: NfcSession,
    appSpendingKey: ByteString,
    appSpendingKeyChaincode: ByteString,
    networkMainnet: Boolean,
    appAuthKey: ByteString,
    serverSpendingKey: ByteString,
    serverSpendingKeyChaincode: ByteString,
    wsmSignature: ByteString,
    accountIndex: UInt = 0u,
  ): String
}

suspend fun NfcCommands.signChallenge(
  session: NfcSession,
  challenge: String,
) = signChallenge(session, challenge.encodeUtf8())

suspend fun NfcCommands.lostAppRecoverySignChallenge(
  session: NfcSession,
  challenge: String,
) = lostAppRecoverySignChallenge(session, challenge.encodeUtf8())

suspend fun NfcCommands.signAccessToken(
  session: NfcSession,
  accessToken: AccessToken,
) = signChallenge(session, accessToken.raw.encodeUtf8())

/**
 * Verifies that the tapped hardware type matches the [expectedType].
 *
 * Calls [getDeviceInfo] to detect the actual hardware type from the device firmware
 * and throws [NfcException.WrongHardwareType] on mismatch. Used during the W3 upgrade
 * flow to enforce that the correct device (W1 vs W3) is tapped at each step.
 */
suspend fun NfcCommands.verifyHardwareType(
  session: NfcSession,
  expectedType: HardwareType,
) {
  val deviceInfo = getDeviceInfo(session)
  val actualType = deviceInfo.hardwareType()
  if (actualType != expectedType) {
    throw NfcException.WrongHardwareType(expected = expectedType, actual = actualType)
  }
}

/**
 * Use the hardware to seal a symmetric key's data.
 *
 * This wraps the NFC Command's `sealData` without needing to
 * expose the raw data of the key.
 */
@OptIn(PrivateData::class)
suspend fun NfcCommands.sealSymmetricKey(
  session: NfcSession,
  key: SymmetricKey,
): SealedData =
  sealData(
    session = session,
    unsealedData = key.raw
  )

/**
 * Use the hardware to unseal a symmetric key's data.
 *
 * This wraps the NFC Command's `unsealData` method, keeping
 * the raw form of the key contained in the returned [SymmetricKey].
 */
@OptIn(PrivateData::class)
suspend fun NfcCommands.unsealSymmetricKey(
  session: NfcSession,
  sealedData: SealedData,
): SymmetricKey =
  unsealData(
    session = session,
    sealedData = sealedData
  ).let { SymmetricKeyImpl(it) }
