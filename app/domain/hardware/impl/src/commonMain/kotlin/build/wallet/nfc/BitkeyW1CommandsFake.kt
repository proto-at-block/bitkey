package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.SealedData
import build.wallet.crypto.SymmetricKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.W1
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.SignatureUtils
import build.wallet.encrypt.signResult
import build.wallet.firmware.*
import build.wallet.firmware.EnrolledFingerprints.Companion.FIRST_FINGERPRINT_INDEX
import build.wallet.firmware.FingerprintEnrollmentStatus.NOT_IN_PROGRESS
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.A
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.grants.*
import build.wallet.nfc.platform.ConfirmationHandles
import build.wallet.nfc.platform.ConfirmationResult
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.HwDisplayPreference
import build.wallet.nfc.platform.KeysetRepairRotateHwKeyParams
import build.wallet.nfc.platform.KeysetRepairRotateHwKeyResult
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.SweepSigningContext
import build.wallet.nfc.transaction.TransactionError
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.mapError
import kotlinx.datetime.Instant
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

@Fake
@BitkeyInject(AppScope::class)
class BitkeyW1CommandsFake(
  private val messageSigner: MessageSigner,
  private val signatureUtils: SignatureUtils,
  @W1 val fakeHardwareKeyStore: FakeHardwareKeyStore,
  @W1 private val fakeHardwareSpendingWalletProvider: FakeHardwareSpendingWalletProvider,
  private val fakeHardwareStatesDao: FakeHardwareStatesDao,
) : NfcCommands {
  var signTransactionRequestCount: Int = 0
    private set

  private var telemetryCoredumpCount: Int = 0
  private var telemetryCoredumpFragmentsByOffset: Map<Int, CoredumpFragment> = emptyMap()

  internal fun setTelemetryCoredump(
    coredumpCount: Int,
    fragmentsByOffset: Map<Int, CoredumpFragment>,
  ) {
    telemetryCoredumpCount = coredumpCount
    telemetryCoredumpFragmentsByOffset = fragmentsByOffset
  }

  internal fun clearTelemetryCoredump() {
    telemetryCoredumpCount = 0
    telemetryCoredumpFragmentsByOffset = emptyMap()
  }

  private val fingerprintEnrollmentResult = FingerprintEnrollmentResult(
    status = NOT_IN_PROGRESS,
    passCount = null,
    failCount = null,
    diagnostics = null
  )
  private var enrolledFingerprints =
    EnrolledFingerprints(
      fingerprintHandles = listOf(
        FingerprintHandle(
          index = FIRST_FINGERPRINT_INDEX,
          label = ""
        )
      )
    )

  override suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
    mcuRole: McuRole,
    version: String,
    deferCommit: Boolean,
  ): HardwareInteraction<Boolean> = HardwareInteraction.Completed(true)

  override suspend fun fwupTransfer(
    session: NfcSession,
    sequenceId: UInt,
    fwupData: List<UByte>,
    offset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole,
  ) = true

  override suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole,
  ) = FwupFinishResponseStatus.Success

  override suspend fun getAuthenticationKey(session: NfcSession) =
    HwAuthPublicKey(fakeHardwareKeyStore.getAuthKeypair().publicKey.pubKey)

  override suspend fun getCoredumpCount(session: NfcSession) = telemetryCoredumpCount

  override suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
    mcuRole: McuRole,
  ): CoredumpFragment {
    return telemetryCoredumpFragmentsByOffset[offset]
      ?: error("No fake telemetry coredump fragment configured for offset=$offset")
  }

  override suspend fun getDeviceInfo(session: NfcSession) = FakeFirmwareDeviceInfo

  override suspend fun getEvents(
    session: NfcSession,
    mcuRole: McuRole,
  ) = EventFragment(emptyList(), 0, null)

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

  override suspend fun getFirmwareMetadata(
    session: NfcSession,
    mcuRole: McuRole,
  ) = FirmwareMetadata(
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
  ) = HwSpendingKeyResult(
    publicKey = HwSpendingPublicKey(
      fakeHardwareKeyStore.getInitialSpendingKeypair(network).publicKey.key
    ),
    attestationSignature = null
  )

  override suspend fun getInitialSpendingPublicKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = HwSpendingPublicKey(
    fakeHardwareKeyStore.getInitialSpendingKeypair(network).publicKey.key
  )

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ) = HwSpendingKeyResult(
    publicKey = HwSpendingPublicKey(
      fakeHardwareKeyStore.getNextSpendingKeypair(
        existingDescriptorPublicKeys.map { it.key.dpub },
        network
      ).publicKey.key
    ),
    attestationSignature = null
  )

  override suspend fun lockDevice(session: NfcSession) = true

  override suspend fun queryAuthentication(session: NfcSession) = true

  override suspend fun showConfirmationScreen(
    session: NfcSession,
    lockOnDismiss: Boolean,
  ) = true

  /**
   * "Seals" some data into a protobuf-like envelope that matches firmware's field layout:
   * data (field 1), nonce (field 2), tag (field 3). The nonce/tag are derived from the fake auth
   * private key so unsealing still fails when the hardware identity changes.
   */
  override suspend fun sealData(
    session: NfcSession,
    unsealedData: ByteString,
  ): SealedData = FakeSealedDataCodec.sealWithKeyStore(fakeHardwareKeyStore, unsealedData)

  override suspend fun unsealData(
    session: NfcSession,
    sealedData: SealedData,
  ): ByteString = FakeSealedDataCodec.unsealWithKeyStore(fakeHardwareKeyStore, sealedData)

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
    displayPreference: HwDisplayPreference?,
    allowUnfinalized: Boolean,
  ): HardwareInteraction<Psbt> {
    signTransactionRequestCount += 1
    if (fakeHardwareStatesDao.getTransactionVerificationEnabled().get() == true) {
      throw TransactionError.VerificationRequired()
    }
    return HardwareInteraction.Completed(
      fakeHardwareSpendingWalletProvider.get(spendingKeyset)
        .signPsbt(psbt)
        .mapError { NfcException.CommandError(cause = it) }
        .getOrThrow()
    )
  }

  override suspend fun sweepTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
    sweepContext: SweepSigningContext,
    displayPreference: HwDisplayPreference?,
  ): HardwareInteraction<Psbt> {
    throw NfcException.CommandError(
      message = "sweepTransaction is not supported on W1 hardware."
    )
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

  override suspend fun wipeDevice(session: NfcSession): HardwareInteraction<Boolean> {
    wipeDevice()
    return HardwareInteraction.Completed(true)
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
  ): GrantRequest =
    buildFakeGrantRequest(
      keyStore = fakeHardwareKeyStore,
      deviceSerial = FakeFirmwareDeviceInfo.serial,
      action = action,
      messageSigner = messageSigner,
      signatureUtils = signatureUtils
    )

  override suspend fun provideGrant(
    session: NfcSession,
    grant: Grant,
  ): Boolean = true

  override suspend fun provisionAppAuthKey(
    session: NfcSession,
    appAuthKey: ByteString,
  ): Boolean = true

  override suspend fun getConfirmationResult(
    session: NfcSession,
    handles: ConfirmationHandles,
  ): ConfirmationResult {
    throw NfcException.CommandError(message = "W1 does not support confirmation protocol")
  }

  @OptIn(bitkey.data.PrivateData::class)
  override suspend fun eekRestorationUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ): HardwareInteraction<SymmetricKey> =
    HardwareInteraction.Completed(
      build.wallet.crypto.SymmetricKeyImpl(unsealData(session, sealedKey))
    )

  @OptIn(bitkey.data.PrivateData::class)
  override suspend fun keysetRepairUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ): HardwareInteraction<SymmetricKey> =
    HardwareInteraction.Completed(
      build.wallet.crypto.SymmetricKeyImpl(unsealData(session, sealedKey))
    )

  override suspend fun keysetRepairRotateHwKey(
    session: NfcSession,
    params: KeysetRepairRotateHwKeyParams,
  ): HardwareInteraction<KeysetRepairRotateHwKeyResult> {
    val hwSpendingKey = getNextSpendingKey(
      session = session,
      existingDescriptorPublicKeys = params.existingHwSpendingKeys,
      network = params.network
    ).publicKey
    val signedAccessToken = signChallenge(session, params.accessToken.raw.encodeUtf8())
    return HardwareInteraction.Completed(
      KeysetRepairRotateHwKeyResult(
        hwSpendingKey = hwSpendingKey,
        signedAccessToken = signedAccessToken
      )
    )
  }
}

internal fun EnrolledFingerprints.insertOrUpdateFingerprintHandle(
  fingerprintHandle: FingerprintHandle,
): EnrolledFingerprints {
  val fingerprints =
    fingerprintHandles.filterNot { it.index == fingerprintHandle.index } + fingerprintHandle
  return EnrolledFingerprints(fingerprintHandles = fingerprints)
}

val FakeFirmwareDeviceInfo = FirmwareDeviceInfo(
  version = "1.2.3",
  serial = "fakeS203serial",
  swType = "dev",
  hwRevision = "evtd",
  activeSlot = FirmwareMetadata.FirmwareSlot.B,
  batteryCharge = 89.45,
  vCell = 1000,
  avgCurrentMa = 1234,
  batteryCycles = 1234,
  secureBootConfig = SecureBootConfig.PROD,
  timeRetrieved = 1691787589,
  bioMatchStats = null,
  mcuInfo = emptyList()
)
