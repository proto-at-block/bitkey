package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.SealedData
import build.wallet.crypto.random.SecureRandom
import build.wallet.crypto.random.nextBytes
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.SignatureUtils
import build.wallet.encrypt.signResult
import build.wallet.firmware.*
import build.wallet.firmware.EnrolledFingerprints.Companion.FIRST_FINGERPRINT_INDEX
import build.wallet.firmware.FingerprintEnrollmentStatus.NOT_IN_PROGRESS
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.A
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.grants.GRANT_CHALLENGE_LEN
import build.wallet.grants.GRANT_DEVICE_ID_LEN
import build.wallet.grants.GRANT_MESSAGE_PREFIX
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.mapError
import kotlinx.datetime.Instant
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

@Fake
@BitkeyInject(AppScope::class)
class NfcCommandsFake(
  private val messageSigner: MessageSigner,
  private val signatureUtils: SignatureUtils,
  val fakeHardwareKeyStore: FakeHardwareKeyStore,
  private val fakeHardwareSpendingWalletProvider: FakeHardwareSpendingWalletProvider,
) : NfcCommands {
  private var fingerprintEnrollmentResult = FingerprintEnrollmentResult(
    status = NOT_IN_PROGRESS,
    passCount = null,
    failCount = null,
    diagnostics = null
  )
  private var enrolledFingerprints =
    EnrolledFingerprints(fingerprintHandles = listOf(FingerprintHandle(index = FIRST_FINGERPRINT_INDEX, label = "")))

  override suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
  ) = true

  override suspend fun fwupTransfer(
    session: NfcSession,
    sequenceId: UInt,
    fwupData: List<UByte>,
    offset: UInt,
    fwupMode: FwupMode,
  ) = true

  override suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
  ) = FwupFinishResponseStatus.Success

  override suspend fun getAuthenticationKey(session: NfcSession) =
    HwAuthPublicKey(fakeHardwareKeyStore.getAuthKeypair().publicKey.pubKey)

  override suspend fun getCoredumpCount(session: NfcSession) = 0

  override suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
  ) = CoredumpFragment(emptyList(), 0, true, 0)

  override suspend fun getDeviceInfo(session: NfcSession) = FakeFirmwareDeviceInfo

  override suspend fun getEvents(session: NfcSession) = EventFragment(emptyList(), 0)

  override suspend fun getFirmwareFeatureFlags(session: NfcSession): List<FirmwareFeatureFlagCfg> {
    return listOf(
      FirmwareFeatureFlagCfg(
        flag = FirmwareFeatureFlag.TELEMETRY,
        enabled = true
      ),
      FirmwareFeatureFlagCfg(
        flag = FirmwareFeatureFlag.DEVICE_INFO_FLAG,
        enabled = true
      ),
      FirmwareFeatureFlagCfg(
        flag = FirmwareFeatureFlag.RATE_LIMIT_TEMPLATE_UPDATE,
        enabled = true
      ),
      FirmwareFeatureFlagCfg(
        flag = FirmwareFeatureFlag.MULTIPLE_FINGERPRINTS,
        enabled = true
      ),
      FirmwareFeatureFlagCfg(
        flag = FirmwareFeatureFlag.FINGERPRINT_RESET,
        enabled = true
      )
    )
  }

  override suspend fun getFingerprintEnrollmentStatus(
    session: NfcSession,
    isEnrollmentContextAware: Boolean,
  ) = fingerprintEnrollmentResult

  override suspend fun deleteFingerprint(
    session: NfcSession,
    index: Int,
  ): Boolean {
    enrolledFingerprints = enrolledFingerprints.copy(
      fingerprintHandles = enrolledFingerprints.fingerprintHandles.filterNot { it.index == index }
    )
    return true
  }

  override suspend fun getUnlockMethod(session: NfcSession) = UnlockInfo(UnlockMethod.BIOMETRICS, 0)

  override suspend fun cancelFingerprintEnrollment(session: NfcSession): Boolean = true

  override suspend fun getEnrolledFingerprints(session: NfcSession): EnrolledFingerprints =
    enrolledFingerprints

  override suspend fun setFingerprintLabel(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ): Boolean {
    enrolledFingerprints = enrolledFingerprints.insertOrUpdateFingerprintHandle(fingerprintHandle)
    return true
  }

  override suspend fun getFirmwareMetadata(session: NfcSession) =
    FirmwareMetadata(
      activeSlot = A,
      gitId = "some-fake-id",
      gitBranch = "main",
      version = "1.0",
      build = "mock",
      timestamp = Instant.DISTANT_PAST,
      hash = ByteString.EMPTY,
      hwRevision = "mocky-mcmockface :)"
    )

  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = HwSpendingPublicKey(fakeHardwareKeyStore.getInitialSpendingKeypair(network).publicKey.key)

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ) = HwSpendingPublicKey(
    fakeHardwareKeyStore.getNextSpendingKeypair(
      existingDescriptorPublicKeys.map { it.key.dpub },
      network
    ).publicKey.key
  )

  override suspend fun lockDevice(session: NfcSession) = true

  override suspend fun queryAuthentication(session: NfcSession) = true

  /** See [NfcCommandsFake.sealKey] for implementation details. */
  private val sealKeySeparator = "---"

  /**
   * "Seals" some data using actual fake auth key. The sealing process is a simple concatenation of the
   * auth private key and the unsealed key in following format: "unsealedData---authPrivateKey".
   *
   * Unsealing process is a simple split of the sealed key by the same separator and then checking if
   * the auth private key is the same as the one used for sealing.
   */
  override suspend fun sealData(
    session: NfcSession,
    unsealedData: ByteString,
  ): SealedData {
    val hwAuthPrivateKey = fakeHardwareKeyStore.getAuthKeypair().privateKey.key
    return buildString {
      append(unsealedData.hex())
      append(sealKeySeparator)
      append(hwAuthPrivateKey.bytes.hex())
    }.encodeUtf8()
  }

  override suspend fun unsealData(
    session: NfcSession,
    sealedData: SealedData,
  ): ByteString {
    val (sealedSekRaw, hwAuthPrivateKeyPart) = sealedData
      .utf8()
      .split(sealKeySeparator)
    // Simulate the sealing process by checking if the private key is the same as the one.
    // If hw auth private keys don't match, effectively means that the data was not sealed with
    // this fake hardware's auth private key.
    require(hwAuthPrivateKeyPart == fakeHardwareKeyStore.getAuthKeypair().privateKey.key.bytes.hex()) {
      "Appropriate fake hw auth private key missing"
    }
    return sealedSekRaw.decodeHex()
  }

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ): String =
    messageSigner
      .signResult(challenge, fakeHardwareKeyStore.getAuthKeypair().privateKey.key)
      .mapError { NfcException.CommandError(cause = it) }
      .getOrThrow()

  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ): Psbt {
    return fakeHardwareSpendingWalletProvider.get(spendingKeyset)
      .signPsbt(psbt)
      .mapError { NfcException.CommandError(cause = it) }
      .getOrThrow()
  }

  override suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ): Boolean {
    enrolledFingerprints = enrolledFingerprints.insertOrUpdateFingerprintHandle(fingerprintHandle)
    // Skip straight to complete state.
    fingerprintEnrollmentResult.status = FingerprintEnrollmentStatus.COMPLETE
    return true
  }

  override suspend fun version(session: NfcSession): UShort = 1u

  suspend fun wipeDevice() {
    fakeHardwareKeyStore.clear()
    fingerprintEnrollmentResult.status = NOT_IN_PROGRESS
  }

  override suspend fun wipeDevice(session: NfcSession): Boolean {
    wipeDevice()
    return true
  }

  override suspend fun getCert(
    session: NfcSession,
    certType: FirmwareCertType,
  ): List<UByte> = emptyList()

  override suspend fun signVerifyAttestationChallenge(
    session: NfcSession,
    deviceIdentityDer: List<UByte>,
    challenge: List<UByte>,
  ): Boolean = true

  override suspend fun getGrantRequest(
    session: NfcSession,
    action: GrantAction,
  ): GrantRequest {
    // Generate 16-byte challenge
    val challengeBytes = SecureRandom().nextBytes(GRANT_CHALLENGE_LEN)

    // Get device ID - use first 8 bytes of device serial converted to bytes
    val deviceIdBytes = FakeFirmwareDeviceInfo.serial.encodeUtf8().toByteArray()
    val deviceId = if (deviceIdBytes.size >= GRANT_DEVICE_ID_LEN) {
      deviceIdBytes.sliceArray(0 until GRANT_DEVICE_ID_LEN)
    } else {
      deviceIdBytes + ByteArray(GRANT_DEVICE_ID_LEN - deviceIdBytes.size)
    }

    val version = 1.toByte()

    // Build the raw message for signing
    val messageToSign = Buffer().apply {
      write(GRANT_MESSAGE_PREFIX.encodeUtf8())
      writeByte(version.toInt())
      write(deviceId)
      write(challengeBytes)
      writeByte(action.value)
    }.readByteString().toByteArray()

    // Sign the message using the hardware auth key
    val authKey = fakeHardwareKeyStore.getAuthKeypair().privateKey.key
    val derSignatureHex = messageSigner.sign(messageToSign.toByteString(), authKey)
    val derSignatureByteString = derSignatureHex.decodeHex().toByteArray().toByteString()
    val compactSignature = signatureUtils.decodeSignatureFromDer(derSignatureByteString)

    // Serialize the GrantRequest fields into a single ByteString
    val buffer = Buffer().apply {
      writeByte(version.toInt())
      write(deviceId)
      write(challengeBytes)
      writeByte(action.value)
      write(compactSignature)
    }
    val serialized = buffer.readByteString()

    // Construct GrantRequest via deserialization
    return GrantRequest.fromBytes(serialized)
      ?: throw NfcException.CommandError("Failed to create GrantRequest from serialized data")
  }

  override suspend fun provideGrant(
    session: NfcSession,
    grant: Grant,
  ): Boolean = true

  private fun EnrolledFingerprints.insertOrUpdateFingerprintHandle(
    fingerprintHandle: FingerprintHandle,
  ): EnrolledFingerprints {
    val fingerprints =
      fingerprintHandles.filterNot { it.index == fingerprintHandle.index } + fingerprintHandle
    return EnrolledFingerprints(fingerprintHandles = fingerprints)
  }
}

val FakeFirmwareDeviceInfo = FirmwareDeviceInfo(
  version = "1.2.3",
  serial = "fake-serial",
  swType = "dev",
  hwRevision = "evtd",
  activeSlot = FirmwareMetadata.FirmwareSlot.B,
  batteryCharge = 89.45,
  vCell = 1000,
  avgCurrentMa = 1234,
  batteryCycles = 1234,
  secureBootConfig = SecureBootConfig.PROD,
  timeRetrieved = 1691787589,
  bioMatchStats = null
)
