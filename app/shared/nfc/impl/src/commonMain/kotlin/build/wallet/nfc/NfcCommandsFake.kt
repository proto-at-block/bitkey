package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.signResult
import build.wallet.firmware.CoredumpFragment
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.EventFragment
import build.wallet.firmware.FingerprintEnrollmentResult
import build.wallet.firmware.FingerprintEnrollmentStatus
import build.wallet.firmware.FingerprintEnrollmentStatus.NOT_IN_PROGRESS
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.A
import build.wallet.firmware.SecureBootConfig
import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.nfc.platform.NfcCommands
import build.wallet.toByteString
import build.wallet.toUByteList
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.mapError
import kotlinx.datetime.Instant
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8

class NfcCommandsFake(
  private val messageSigner: MessageSigner,
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
    EnrolledFingerprints(3, listOf(FingerprintHandle(index = 0, label = "")))

  suspend fun clearHardwareKeysAndFingerprintEnrollment() {
    fakeHardwareKeyStore.clear()
    fingerprintEnrollmentResult.status = NOT_IN_PROGRESS
  }

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

  override suspend fun getDeviceInfo(session: NfcSession) =
    FirmwareDeviceInfo(
      version = "1.2.3",
      serial = "serial",
      swType = "dev",
      hwRevision = "evtd",
      activeSlot = FirmwareMetadata.FirmwareSlot.B,
      batteryCharge = 89.45,
      vCell = 1000,
      avgCurrentMa = 1234,
      batteryCycles = 1234,
      secureBootConfig = SecureBootConfig.PROD,
      timeRetrieved = 1691787589
    )

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
      maxCount = 3,
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
   * "Seals" a CSEK using actual fake auth key. The sealing process is a simple concatenation of the
   * auth private key and the unsealed key in following format: "unsealedKey---authPrivateKey".
   *
   * Unsealing process is a simple split of the sealed key by the same separator and then checking if
   * the auth private key is the same as the one used for sealing.
   */
  override suspend fun sealKey(
    session: NfcSession,
    unsealedKey: Csek,
  ): SealedCsek {
    val hwAuthPrivateKey = fakeHardwareKeyStore.getAuthKeypair().privateKey.key
    return buildString {
      append(unsealedKey.key.raw.hex())
      append(sealKeySeparator)
      append(hwAuthPrivateKey.bytes.hex())
    }.encodeUtf8()
  }

  /**
   * See [NfcCommandsFake.sealKey] for implementation details.
   */
  override suspend fun unsealKey(
    session: NfcSession,
    sealedKey: List<UByte>,
  ): List<UByte> {
    val (sealedCsekRaw, hwAuthPrivateKeyPart) = sealedKey.toByteString()
      .utf8()
      .split(sealKeySeparator)
    // Simulate the sealing process by checking if the private key is the same as the one.
    // If hw auth private keys don't match, effectively means that the csek was not sealed with
    // this fake hardware's auth private key.
    require(hwAuthPrivateKeyPart == fakeHardwareKeyStore.getAuthKeypair().privateKey.key.bytes.hex()) {
      "Appropriate fake hw auth private key missing"
    }
    return sealedCsekRaw.decodeHex().toUByteList()
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

  override suspend fun wipeDevice(session: NfcSession): Boolean {
    clearHardwareKeysAndFingerprintEnrollment()
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

  private fun EnrolledFingerprints.insertOrUpdateFingerprintHandle(
    fingerprintHandle: FingerprintHandle,
  ): EnrolledFingerprints {
    val fingerprints =
      fingerprintHandles.filterNot { it.index == fingerprintHandle.index } + fingerprintHandle
    return EnrolledFingerprints(3, fingerprints)
  }
}
