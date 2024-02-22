package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.Csek
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.signResult
import build.wallet.firmware.CoredumpFragment
import build.wallet.firmware.EventFragment
import build.wallet.firmware.FingerprintEnrollmentStatus
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.A
import build.wallet.firmware.SecureBootConfig
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.mapError
import kotlinx.datetime.Instant
import okio.ByteString

class NfcCommandsFake(
  private val messageSigner: MessageSigner,
  val fakeHardwareKeyStore: FakeHardwareKeyStore,
  private val fakeHardwareSpendingWalletProvider: FakeHardwareSpendingWalletProvider,
) : NfcCommands {
  suspend fun clearHardwareKeys() {
    fakeHardwareKeyStore.clear()
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
        flag = FirmwareFeatureFlag.Telemetry,
        enabled = true
      ),
      FirmwareFeatureFlagCfg(
        flag = FirmwareFeatureFlag.DeviceInfoFlag,
        enabled = true
      ),
      FirmwareFeatureFlagCfg(
        flag = FirmwareFeatureFlag.RateLimitTemplateUpdate,
        enabled = true
      )
    )
  }

  override suspend fun getFingerprintEnrollmentStatus(session: NfcSession) =
    FingerprintEnrollmentStatus.COMPLETE

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

  override suspend fun sealKey(
    session: NfcSession,
    unsealedKey: Csek,
  ) = unsealedKey.key.raw

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ) = messageSigner
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

  override suspend fun startFingerprintEnrollment(session: NfcSession) = true

  override suspend fun unsealKey(
    session: NfcSession,
    sealedKey: List<UByte>,
  ) = sealedKey

  override suspend fun version(session: NfcSession): UShort = 1u

  override suspend fun wipeDevice(session: NfcSession) = true.also { fakeHardwareKeyStore.clear() }

  override suspend fun getCert(
    session: NfcSession,
    certType: FirmwareCertType,
  ): List<UByte> = emptyList()

  override suspend fun signVerifyAttestationChallenge(
    session: NfcSession,
    deviceIdentityDer: List<UByte>,
    challenge: List<UByte>,
  ): Boolean = true
}
