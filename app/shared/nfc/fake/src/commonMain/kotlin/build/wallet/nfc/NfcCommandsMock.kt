package build.wallet.nfc

import app.cash.turbine.Turbine
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.Csek
import build.wallet.firmware.CoredumpFragment
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.EventFragment
import build.wallet.firmware.FingerprintEnrollmentStatus
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.firmware.FirmwareMetadataMock
import build.wallet.firmware.UnlockInfo
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.money.BitcoinMoney
import build.wallet.nfc.platform.NfcCommands
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

class NfcCommandsMock(turbine: (String) -> Turbine<Any>) : NfcCommands {
  val signTransactionCalls = turbine("SignTransaction calls")
  private var keyIndex = 0

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

  override suspend fun getAuthenticationKey(session: NfcSession) = HwAuthSecp256k1PublicKeyMock

  override suspend fun getCoredumpCount(session: NfcSession) = 0

  override suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
  ) = CoredumpFragment(emptyList(), 0, true, 0)

  override suspend fun getDeviceInfo(session: NfcSession) = FirmwareDeviceInfoMock

  override suspend fun getEvents(session: NfcSession) = EventFragment(emptyList(), 0)

  override suspend fun getFirmwareFeatureFlags(session: NfcSession): List<FirmwareFeatureFlagCfg> =
    emptyList()

  override suspend fun getFingerprintEnrollmentStatus(session: NfcSession) =
    FingerprintEnrollmentStatus.COMPLETE

  override suspend fun deleteFingerprint(
    session: NfcSession,
    index: Int,
  ): Boolean = true

  override suspend fun getEnrolledFingerprints(session: NfcSession) =
    EnrolledFingerprints(3, emptyList())

  override suspend fun setFingerprintLabel(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ): Boolean = true

  override suspend fun getUnlockMethod(session: NfcSession): UnlockInfo {
    TODO("Not yet implemented")
  }

  override suspend fun getFirmwareMetadata(session: NfcSession) = FirmwareMetadataMock

  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = spendingPublicKey(0)

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ): HwSpendingPublicKey {
    keyIndex += 1
    return spendingPublicKey(keyIndex)
  }

  override suspend fun lockDevice(session: NfcSession) = true

  override suspend fun queryAuthentication(session: NfcSession) = true

  override suspend fun sealKey(
    session: NfcSession,
    unsealedKey: Csek,
  ) = "sealed-key".encodeUtf8()

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ) = "signed-challenge-of-$challenge"

  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ) = Psbt(
    id = "psbt-id",
    base64 = "some-base-64",
    fee = BitcoinMoney.sats(10_000),
    baseSize = 10000,
    numOfInputs = 1,
    amountSats = 10000UL
  ).also { signTransactionCalls.add(psbt) }

  override suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = true

  override suspend fun unsealKey(
    session: NfcSession,
    sealedKey: List<UByte>,
  ) = "unsealed-key".encodeUtf8().toByteArray().map { it.toUByte() }

  override suspend fun version(session: NfcSession): UShort = 1u

  override suspend fun wipeDevice(session: NfcSession) = false // Can't wipe a fake device!

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

private fun spendingPublicKey(index: Int) =
  HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-$index"))
