package build.wallet.nfc

import app.cash.turbine.Turbine
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.SealedData
import build.wallet.firmware.CoredumpFragment
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.EventFragment
import build.wallet.firmware.FingerprintEnrollmentResult
import build.wallet.firmware.FingerprintEnrollmentStatus
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.firmware.FirmwareMetadataMock
import build.wallet.firmware.UnlockInfo
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.money.BitcoinMoney
import build.wallet.nfc.platform.NfcCommands
import io.ktor.utils.io.core.toByteArray
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8

class NfcCommandsMock(
  turbine: ((String) -> Turbine<Any>),
) : NfcCommands {
  val signTransactionCalls = turbine.invoke("SignTransaction calls")
  val cancelFingerprintEnrollmentCalls = turbine.invoke("CancelFingerprintEnrollment calls")
  val getEnrolledFingerprintsCalls = turbine.invoke("GetEnrolledFingerprints calls")
  val deleteFingerprintCalls = turbine.invoke("DeleteFingerprint calls")
  val startFingerprintEnrollmentCalls = turbine.invoke("StartFingerprintEnrollment calls")
  val setFingerprintLabelCalls = turbine.invoke("SetFingerprintLabel calls")
  val getGrantRequestCalls = turbine.invoke("GetGrantRequest calls")
  val provideGrantCalls = turbine.invoke("ProvideGrant calls")
  val getNextSpendingKeyCalls = turbine.invoke("GetNextSpendingKey calls")

  private val defaultEnrollmentResult = FingerprintEnrollmentResult(
    status = FingerprintEnrollmentStatus.COMPLETE,
    passCount = null,
    failCount = null,
    diagnostics = null
  )
  private val defaultEnrolledFingerprints = EnrolledFingerprints(fingerprintHandles = emptyList())
  private val defaultFirmwareFeatureFlags = listOf(
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
  private val defaultProvideGrantResult = true
  private val defaultStartFingerprintEnrollmentResult = true
  private val defaultDeleteFingerprintResult = true

  private var enrollmentResult = defaultEnrollmentResult
  private var enrolledFingerprints = defaultEnrolledFingerprints
  private var firmwareFeatureFlags = defaultFirmwareFeatureFlags
  private var provideGrantResult = defaultProvideGrantResult
  private var startFingerprintEnrollmentResult = defaultStartFingerprintEnrollmentResult
  private var deleteFingerprintResult = defaultDeleteFingerprintResult

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
    firmwareFeatureFlags

  override suspend fun getFingerprintEnrollmentStatus(
    session: NfcSession,
    isEnrollmentContextAware: Boolean,
  ) = enrollmentResult

  override suspend fun deleteFingerprint(
    session: NfcSession,
    index: Int,
  ): Boolean = deleteFingerprintResult.also { deleteFingerprintCalls.add(index) }

  override suspend fun getEnrolledFingerprints(session: NfcSession) =
    enrolledFingerprints
      .also { getEnrolledFingerprintsCalls.add(it) }

  override suspend fun setFingerprintLabel(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ): Boolean = true.also { setFingerprintLabelCalls.add(Unit) }

  override suspend fun getUnlockMethod(session: NfcSession): UnlockInfo {
    TODO("Not yet implemented")
  }

  override suspend fun cancelFingerprintEnrollment(session: NfcSession): Boolean =
    true.also { cancelFingerprintEnrollmentCalls.add(Unit) }

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
    getNextSpendingKeyCalls.add(existingDescriptorPublicKeys)
    keyIndex += 1
    return spendingPublicKey(keyIndex)
  }

  override suspend fun lockDevice(session: NfcSession) = true

  override suspend fun queryAuthentication(session: NfcSession) = true

  override suspend fun sealData(
    session: NfcSession,
    unsealedData: ByteString,
  ) = "sealed-data".encodeUtf8()

  override suspend fun unsealData(
    session: NfcSession,
    sealedData: SealedData,
  ) = "unsealed-data".encodeUtf8()

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
  ) =
    startFingerprintEnrollmentResult.also { startFingerprintEnrollmentCalls.add(fingerprintHandle) }

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

  override suspend fun getGrantRequest(
    session: NfcSession,
    action: GrantAction,
  ): GrantRequest {
    return when (action) {
      GrantAction.FINGERPRINT_RESET -> {
        GrantRequest(
          version = 0x01,
          deviceId = ByteArray(8) { 0x01 },
          challenge = ByteArray(16) { 0x02 },
          action = action,
          signature = "21a1aa12efc8512727856a9ccc428a511cf08b211f26551781ae0a37661de8060c566ded9486500f6927e9c9df620c65653c68316e61930a49ecab31b3bec498".decodeHex().toByteArray()
        ).also { getGrantRequestCalls.add(action) }
      }
      else -> {
        throw IllegalArgumentException(
          "Unsupported GrantAction: $action"
        )
      }
    }
  }

  override suspend fun provideGrant(
    session: NfcSession,
    grant: Grant,
  ) = provideGrantResult.also { provideGrantCalls.add(grant) }

  fun setEnrollmentStatus(enrollmentStatus: FingerprintEnrollmentStatus) {
    this.enrollmentResult.status = enrollmentStatus
  }

  fun setEnrolledFingerprints(enrolledFingerprints: EnrolledFingerprints) {
    this.enrolledFingerprints = enrolledFingerprints
  }

  fun setFirmwareFeatureFlags(firmwareFeatureFlags: List<FirmwareFeatureFlagCfg>) {
    this.firmwareFeatureFlags = firmwareFeatureFlags
  }

  fun setProvideGrantResult(result: Boolean) {
    this.provideGrantResult = result
  }

  fun setStartFingerprintEnrollmentResult(result: Boolean) {
    this.startFingerprintEnrollmentResult = result
  }

  fun setDeleteFingerprintResult(result: Boolean) {
    this.deleteFingerprintResult = result
  }

  fun reset() {
    enrollmentResult = defaultEnrollmentResult
    enrolledFingerprints = defaultEnrolledFingerprints
    firmwareFeatureFlags = defaultFirmwareFeatureFlags
    provideGrantResult = defaultProvideGrantResult
    startFingerprintEnrollmentResult = defaultStartFingerprintEnrollmentResult
    deleteFingerprintResult = defaultDeleteFingerprintResult
  }
}

private fun spendingPublicKey(index: Int) =
  HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-$index"))
