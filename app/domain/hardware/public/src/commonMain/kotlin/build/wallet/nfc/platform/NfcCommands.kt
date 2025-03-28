package build.wallet.nfc.platform

import bitkey.auth.AccessToken
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.crypto.SealedData
import build.wallet.firmware.CoredumpFragment
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.EventFragment
import build.wallet.firmware.FingerprintEnrollmentResult
import build.wallet.firmware.FingerprintEnrollmentStatus
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.UnlockInfo
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.nfc.NfcSession
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * [NfcCommands] has a method for each primitive (core) NFC command (e.g. version or sealKey),
 * each taking an [NfcSession] as well as their respective arguments.
 */

@Suppress("TooManyFunctions")
interface NfcCommands {
  /**
   * Start FWUP process.
   */
  suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
  ): Boolean

  /**
   * Incremental transfer for FWUP.
   */
  suspend fun fwupTransfer(
    session: NfcSession,
    sequenceId: UInt,
    fwupData: List<UByte>,
    offset: UInt,
    fwupMode: FwupMode,
  ): Boolean

  /**
   * Finish FWUP process.
   */
  suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
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
  ): CoredumpFragment

  /**
   * Get device info for the firmware on the hardware device.
   */
  suspend fun getDeviceInfo(session: NfcSession): FirmwareDeviceInfo

  /**
   * Get events for firmware telemetry.
   */
  suspend fun getEvents(session: NfcSession): EventFragment

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
   */
  suspend fun getFirmwareMetadata(session: NfcSession): FirmwareMetadata

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

  suspend fun sealData(
    session: NfcSession,
    unsealedData: ByteString,
  ): SealedData

  suspend fun unsealData(
    session: NfcSession,
    sealedData: SealedData,
  ): ByteString

  suspend fun sealKey(
    session: NfcSession,
    unsealedKey: Csek,
  ): SealedCsek

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
   *
   * @return A PSBT with the hardware signature.
   */
  suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ): Psbt

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
    fingerprintHandle: FingerprintHandle = FingerprintHandle(0, ""),
  ): Boolean

  /**
   * Unseal a previously sealed key obtained from the [sealKey] command.
   *
   * @param sealedKey: The sealed key, aka "sealant" returned by the hardware after [sealKey].
   */
  suspend fun unsealKey(
    session: NfcSession,
    sealedKey: List<UByte>,
  ): List<UByte>

  /**
   * Get the current version of the hardware device.
   */
  suspend fun version(session: NfcSession): UShort

  /**
   * Wipe the keys on the hardware device.
   */
  suspend fun wipeDevice(session: NfcSession): Boolean

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
}

suspend fun NfcCommands.signChallenge(
  session: NfcSession,
  challenge: String,
) = signChallenge(session, challenge.encodeUtf8())

suspend fun NfcCommands.signAccessToken(
  session: NfcSession,
  accessToken: AccessToken,
) = signChallenge(session, accessToken.raw.encodeUtf8())
