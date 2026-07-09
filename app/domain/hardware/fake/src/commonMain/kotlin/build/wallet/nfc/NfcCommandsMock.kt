package build.wallet.nfc

import app.cash.turbine.Turbine
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.keys.DescriptorPublicKey
import build.wallet.bitcoin.keys.DescriptorPublicKeyMock
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.auth.HwAuthSecp256k1PublicKeyMock
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.SealedData
import build.wallet.crypto.SymmetricKey
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.firmware.*
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.money.BitcoinMoney
import build.wallet.nfc.platform.*
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8

open class NfcCommandsMock(
  turbine: ((String) -> Turbine<Any>),
) : NfcCommands, HardwareIdentityAwareNfcCommands {
  val signTransactionCalls = turbine.invoke("SignTransaction calls")
  val sweepTransactionCalls = turbine.invoke("SweepTransaction calls")
  val getConfirmationResultCalls = turbine.invoke("GetConfirmationResult calls")
  val cancelFingerprintEnrollmentCalls = turbine.invoke("CancelFingerprintEnrollment calls")
  val getEnrolledFingerprintsCalls = turbine.invoke("GetEnrolledFingerprints calls")
  val deleteFingerprintCalls = turbine.invoke("DeleteFingerprint calls")
  val startFingerprintEnrollmentCalls = turbine.invoke("StartFingerprintEnrollment calls")
  val setFingerprintLabelCalls = turbine.invoke("SetFingerprintLabel calls")
  val getGrantRequestCalls = turbine.invoke("GetGrantRequest calls")
  val provideGrantCalls = turbine.invoke("ProvideGrant calls")
  val getNextSpendingKeyCalls = turbine.invoke("GetNextSpendingKey calls")
  val provisionAppAuthKeyCalls = turbine.invoke("ProvisionAppAuthKey calls")
  val getDeviceInfoCalls = turbine.invoke("GetDeviceInfo calls")
  val getAuthenticationKeyCalls = turbine.invoke("GetAuthenticationKey calls")
  val lostAppRecoveryCalls = turbine.invoke("LostAppRecovery calls")
  val lostAppRecoveryContinueParamsCalls = turbine.invoke("LostAppRecoveryContinueParams calls")
  val rotateAppAuthKeysCalls = turbine.invoke("RotateAppAuthKeys calls")
  val upgradeRotateAppAuthKeysCalls = turbine.invoke("UpgradeRotateAppAuthKeys calls")
  var lastSignTransactionAllowUnfinalized: Boolean? = null
    private set

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
  private val defaultLostAppRecoveryResult = HardwareInteraction.Completed(
    LostAppRecoveryCompositeResult(
      actionProofSignature = "0".repeat(128),
      spendingKeyDpub = DescriptorPublicKey("[34eae6a8/84'/0'/0']xpubDDj952KUFGTDcNV1qY5Tuevm6vnBWK8NSpTTkCz1XTApv2SeDaqcrUTBgDdCRF9KmtxV33R8E9NtSi9VSBUPj4M3fKr4uk3kRy8Vbo1LbAv/*"),
      appAuthKeySignature = "0".repeat(128)
    )
  )
  private val defaultSignTransactionResult = HardwareInteraction.Completed(
    Psbt(
      id = "psbt-id",
      base64 = "some-base-64",
      fee = Fee(amount = BitcoinMoney.sats(10_000)),
      vsize = 10000,
      numOfInputs = 1,
      amountSats = 10000UL
    )
  )
  private val defaultConfirmationResult: ConfirmationResult =
    ConfirmationResult.WipeDevice(success = true)

  private var enrollmentResult = defaultEnrollmentResult
  private var enrolledFingerprints = defaultEnrolledFingerprints
  private var firmwareFeatureFlags = defaultFirmwareFeatureFlags
  private var provideGrantResult = defaultProvideGrantResult
  private var startFingerprintEnrollmentResult = defaultStartFingerprintEnrollmentResult
  private var deleteFingerprintResult = defaultDeleteFingerprintResult
  var signTransactionResult: HardwareInteraction<Psbt> = defaultSignTransactionResult
  var confirmationResult: ConfirmationResult = defaultConfirmationResult
  var shouldInvokeLostAppRecoveryContinue = false
  var lostAppRecoveryUnsealedSsek: SymmetricKey = SymmetricKeyImpl("unsealed-ssek".encodeUtf8())
  var lostAppRecoveryResult: HardwareInteraction<LostAppRecoveryCompositeResult> =
    defaultLostAppRecoveryResult
  var authenticationKeyResult: HwAuthPublicKey = HwAuthSecp256k1PublicKeyMock

  private var keyIndex = 0

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
    authenticationKeyResult.also {
      getAuthenticationKeyCalls.add(Unit)
    }

  override suspend fun getCoredumpCount(session: NfcSession) = 0

  override suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
    mcuRole: McuRole,
  ) = CoredumpFragment(emptyList(), 0, true, 0, McuRole.CORE, McuName.EFR32)

  var deviceInfoResult: FirmwareDeviceInfo = FirmwareDeviceInfoMock

  override suspend fun getDeviceInfo(session: NfcSession) =
    deviceInfoResult.also {
      getDeviceInfoCalls.add(it)
    }

  override suspend fun resolvedDeviceInfo(session: NfcSession): FirmwareDeviceInfo =
    deviceInfoResult

  override suspend fun getEvents(
    session: NfcSession,
    mcuRole: McuRole,
  ) = EventFragment(emptyList(), 0, null)

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

  override suspend fun getFirmwareMetadata(
    session: NfcSession,
    mcuRole: McuRole,
  ) = FirmwareMetadataMock

  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = HwSpendingKeyResult(publicKey = spendingPublicKey(0), attestationSignature = null)

  override suspend fun getInitialSpendingPublicKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = spendingPublicKey(0)

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ): HwSpendingKeyResult {
    getNextSpendingKeyCalls.add(existingDescriptorPublicKeys)
    keyIndex += 1
    return HwSpendingKeyResult(publicKey = spendingPublicKey(keyIndex), attestationSignature = null)
  }

  override suspend fun lockDevice(session: NfcSession) = true

  override suspend fun queryAuthentication(session: NfcSession) = true

  override suspend fun showConfirmationScreen(
    session: NfcSession,
    lockOnDismiss: Boolean,
  ) = true

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
    displayPreference: HwDisplayPreference?,
    allowUnfinalized: Boolean,
  ) = signTransactionResult.also {
    lastSignTransactionAllowUnfinalized = allowUnfinalized
    signTransactionCalls.add(psbt)
  }

  override suspend fun sweepTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
    sweepContext: SweepSigningContext,
    displayPreference: HwDisplayPreference?,
  ) = HardwareInteraction.Completed(
    Psbt(
      id = "psbt-id",
      base64 = "some-base-64",
      fee = Fee(amount = BitcoinMoney.sats(10_000)),
      vsize = 10000,
      numOfInputs = 1,
      amountSats = 10000UL
    ).also { sweepTransactionCalls.add(psbt to sweepContext) }
  )

  override suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) =
    startFingerprintEnrollmentResult.also { startFingerprintEnrollmentCalls.add(fingerprintHandle) }

  override suspend fun version(session: NfcSession): UShort = 1u

  override suspend fun wipeDevice(session: NfcSession): HardwareInteraction<Boolean> =
    HardwareInteraction.Completed(false)

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
          signature = "21a1aa12efc8512727856a9ccc428a511cf08b211f26551781ae0a37661de8060c566ded9486500f6927e9c9df620c65653c68316e61930a49ecab31b3bec498".decodeHex()
            .toByteArray()
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

  override suspend fun provisionAppAuthKey(
    session: NfcSession,
    appAuthKey: ByteString,
  ) = true.also { provisionAppAuthKeyCalls.add(appAuthKey) }

  override suspend fun getConfirmationResult(
    session: NfcSession,
    handles: ConfirmationHandles,
  ): ConfirmationResult = confirmationResult.also { getConfirmationResultCalls.add(handles) }

  suspend fun getAddress(
    session: NfcSession,
    addressIndex: UInt,
  ): String = "bc1q_mock_$addressIndex"

  suspend fun verifyKeysAndBuildDescriptor(
    session: NfcSession,
    appSpendingKey: ByteString,
    appSpendingKeyChaincode: ByteString,
    networkMainnet: Boolean,
    appAuthKey: ByteString,
    serverSpendingKey: ByteString,
    serverSpendingKeyChaincode: ByteString,
    wsmSignature: ByteString,
    accountIndex: UInt,
  ): String = "mock-app-auth-key-hw-signature"

  suspend fun signActionProof(
    session: NfcSession,
    version: UInt,
    action: ActionProofAction,
    value: String?,
    bindings: String,
  ): HardwareInteraction<String> =
    HardwareInteraction.Completed(
      // Valid 65-byte hex-encoded signature (130 lowercase hex chars) for test compatibility
      "0".repeat(130)
    )

  override suspend fun eekRestorationUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ): HardwareInteraction<SymmetricKey> =
    HardwareInteraction.Completed(
      SymmetricKeyImpl("mock-unsealed-eek-key".encodeUtf8())
    )

  override suspend fun keysetRepairUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ): HardwareInteraction<SymmetricKey> =
    HardwareInteraction.Completed(
      SymmetricKeyImpl("mock-unsealed-keyset-repair-key".encodeUtf8())
    )

  override suspend fun keysetRepairRotateHwKey(
    session: NfcSession,
    params: KeysetRepairRotateHwKeyParams,
  ): HardwareInteraction<KeysetRepairRotateHwKeyResult> {
    keyIndex += 1
    return HardwareInteraction.Completed(
      KeysetRepairRotateHwKeyResult(
        hwSpendingKey = spendingPublicKey(keyIndex),
        signedAccessToken = "0".repeat(130)
      )
    )
  }

  var fullAccountCloudBackupRestorationResult: CsekUnsealResult =
    CsekUnsealResult(index = 0, unsealedCsek = SymmetricKeyImpl("mock-unsealed-csek".encodeUtf8()))

  suspend fun <T> fullAccountCloudBackupRestoration(
    session: NfcSession,
    sealedCseks: List<SealedData>,
    onCsekUnsealed: suspend (CsekUnsealResult) -> T,
  ): HardwareInteraction<T> =
    HardwareInteraction.Completed(onCsekUnsealed(fullAccountCloudBackupRestorationResult))

  suspend fun lostAppRecovery(
    session: NfcSession,
    sealedSsek: ByteString,
    onSsekUnsealed: suspend (SymmetricKey) -> LostAppRecoveryContinueParams,
  ): HardwareInteraction<LostAppRecoveryCompositeResult> {
    lostAppRecoveryCalls.add(sealedSsek)
    if (shouldInvokeLostAppRecoveryContinue) {
      lostAppRecoveryContinueParamsCalls.add(onSsekUnsealed(lostAppRecoveryUnsealedSsek))
    }
    return lostAppRecoveryResult
  }

  val signChallengeAndSealSeksCalls = turbine.invoke("SignChallengeAndSealSeks calls")
  var signChallengeAndSealSeksResult: HardwareInteraction<SignChallengeAndSealSeksResult> =
    HardwareInteraction.Completed(
      SignChallengeAndSealSeksResult(
        signedChallenge = "mock-signed-challenge",
        sealedCsek = VALID_FIRMWARE_SEALED_DATA,
        sealedSsek = VALID_FIRMWARE_SEALED_DATA
      )
    )

  suspend fun signChallengeAndSealSeks(
    session: NfcSession,
    challenge: ByteString,
    unsealedCsek: ByteString,
    unsealedSsek: ByteString,
  ): HardwareInteraction<SignChallengeAndSealSeksResult> {
    signChallengeAndSealSeksCalls.add(challenge)
    return signChallengeAndSealSeksResult
  }

  val recoveryAuthorizeLostAppCalls = turbine.invoke("RecoveryAuthorizeLostApp calls")
  var recoveryAuthorizeLostAppResult: HardwareInteraction<RecoveryAuthorizeLostAppResult> =
    HardwareInteraction.Completed(
      RecoveryAuthorizeLostAppResult(
        descriptorBackupsSignature = "mock-descriptor-backups-sig",
        activateKeysetSignature = "mock-activate-keyset-sig",
        unsealedDdkData = null,
        unsealedSsek = null
      )
    )

  suspend fun recoveryAuthorizeLostApp(
    session: NfcSession,
    sealedDdkData: SealedData?,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<RecoveryAuthorizeLostAppResult> {
    recoveryAuthorizeLostAppCalls.add(Unit)
    return recoveryAuthorizeLostAppResult
  }

  val recoveryAuthorizeLostHwCalls = turbine.invoke("RecoveryAuthorizeLostHw calls")
  var recoveryAuthorizeLostHwResult: HardwareInteraction<RecoveryAuthorizeLostHwResult> =
    HardwareInteraction.Completed(
      RecoveryAuthorizeLostHwResult(
        descriptorBackupsSignature = "mock-descriptor-backups-sig",
        activateKeysetSignature = "mock-activate-keyset-sig",
        sealedDdkData = null
      )
    )

  suspend fun recoveryAuthorizeLostHw(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<RecoveryAuthorizeLostHwResult> {
    recoveryAuthorizeLostHwCalls.add(Unit)
    return recoveryAuthorizeLostHwResult
  }

  val upgradeAuthorizeW3Calls = turbine.invoke("UpgradeAuthorizeW3 calls")
  var upgradeAuthorizeW3Result: HardwareInteraction<UpgradeAuthorizeW3Result> =
    HardwareInteraction.Completed(
      UpgradeAuthorizeW3Result(
        descriptorBackupsSignature = "mock-descriptor-backups-sig",
        activateKeysetSignature = "mock-activate-keyset-sig",
        sealedDdkData = "mock-sealed-ddk".encodeUtf8()
      )
    )

  suspend fun upgradeAuthorizeW3(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<UpgradeAuthorizeW3Result> {
    upgradeAuthorizeW3Calls.add(Unit)
    return upgradeAuthorizeW3Result
  }

  suspend fun lostAppRecoverySignChallenge(
    session: NfcSession,
    challenge: ByteString,
  ): HardwareInteraction<String> = HardwareInteraction.Completed("signed-challenge-of-$challenge")

  var rotateAppAuthKeysResult: HardwareInteraction<RotateAppAuthKeysCompositeResult> =
    HardwareInteraction.Completed(
      RotateAppAuthKeysCompositeResult(
        actionProofSignature = "fake-action-proof-signature",
        hwSignedAccountId = "fake-hw-signed-account-id",
        appGlobalAuthKeyHwSignature = "fake-app-global-auth-key-hw-signature",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock
      )
    )

  suspend fun rotateAppAuthKeys(
    session: NfcSession,
    params: RotateAppAuthKeysContinueParams,
  ): HardwareInteraction<RotateAppAuthKeysCompositeResult> {
    rotateAppAuthKeysCalls.add(params)
    return rotateAppAuthKeysResult
  }

  var upgradeRotateAppAuthKeysResult: HardwareInteraction<UpgradeRotateAppAuthKeysResult> =
    HardwareInteraction.Completed(
      UpgradeRotateAppAuthKeysResult(
        hwSignedAccountId = "fake-hw-signed-account-id",
        appGlobalAuthKeyHwSignature = "fake-app-global-auth-key-hw-signature",
        hwAuthPublicKey = HwAuthSecp256k1PublicKeyMock
      )
    )

  suspend fun upgradeRotateAppAuthKeys(
    session: NfcSession,
    params: UpgradeRotateAppAuthKeysParams,
  ): HardwareInteraction<UpgradeRotateAppAuthKeysResult> {
    upgradeRotateAppAuthKeysCalls.add(params)
    return upgradeRotateAppAuthKeysResult
  }

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
    signTransactionResult = defaultSignTransactionResult
    confirmationResult = defaultConfirmationResult
    shouldInvokeLostAppRecoveryContinue = false
    lostAppRecoveryUnsealedSsek = SymmetricKeyImpl("unsealed-ssek".encodeUtf8())
    lostAppRecoveryResult = defaultLostAppRecoveryResult
    authenticationKeyResult = HwAuthSecp256k1PublicKeyMock
    deviceInfoResult = FirmwareDeviceInfoMock
    lastSignTransactionAllowUnfinalized = null
  }
}

class W3NfcCommandsMock(
  turbine: ((String) -> Turbine<Any>),
) : NfcCommandsMock(turbine), W3NfcCommands

private fun spendingPublicKey(index: Int) =
  HwSpendingPublicKey(DescriptorPublicKeyMock(identifier = "hardware-dpub-$index"))

private val VALID_FIRMWARE_SEALED_DATA =
  "0a20b8ef0c208d341bf262638a7ecf142bea1234567890abcdef1234567890abcdef120c0102030405060708090a0b0c1a1000112233445566778899aabbccddeeff"
    .decodeHex()
