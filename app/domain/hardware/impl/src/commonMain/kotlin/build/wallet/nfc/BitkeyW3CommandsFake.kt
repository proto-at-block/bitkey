package build.wallet.nfc

import bitkey.account.AccountConfigService
import bitkey.account.HardwareType
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.crypto.SealedData
import build.wallet.crypto.SymmetricKey
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.W3
import build.wallet.encrypt.MessageSigner
import build.wallet.encrypt.SignatureUtils
import build.wallet.encrypt.signResult
import build.wallet.firmware.*
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.grants.*
import build.wallet.money.display.BitcoinDisplayUnit
import build.wallet.nfc.platform.ActionProofAction
import build.wallet.nfc.platform.CsekUnsealResult
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.HwDisplayPreference
import build.wallet.nfc.platform.LostAppRecoveryCompositeResult
import build.wallet.nfc.platform.LostAppRecoveryContinueParams
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.RecoveryAuthorizeLostAppResult
import build.wallet.nfc.platform.RecoveryAuthorizeLostHwResult
import build.wallet.nfc.platform.RotateAppAuthKeysCompositeResult
import build.wallet.nfc.platform.RotateAppAuthKeysContinueParams
import build.wallet.nfc.platform.SignChallengeAndSealSeksResult
import build.wallet.nfc.platform.UpgradeAuthorizeW3Result
import build.wallet.nfc.platform.UpgradeRotateAppAuthKeysParams
import build.wallet.nfc.platform.UpgradeRotateAppAuthKeysResult
import build.wallet.nfc.platform.unsealSymmetricKey
import build.wallet.nfc.transaction.TransactionError
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.mapError
import kotlinx.datetime.Instant
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

private const val UNIT_SEPARATOR: Int = 0x1F

/**
 * Fake implementation of NFC commands for the W3.
 *
 * Delegates to W1 fake commands for stateless operations (telemetry, firmware flags, certs).
 * Shared operations that the real W3 wrapper delegates (device info, auth/seal/unseal, spending
 * keys) follow the configured fake hardware type from [accountConfigService]. W3-only composites
 * and per-device state still use W3's own key store and local fields, giving the W3 fake an
 * independent identity when the tests present W3 hardware.
 */
@Suppress("LargeClass")
@BitkeyInject(AppScope::class)
class BitkeyW3CommandsFake(
  private val w1CommandsFake: BitkeyW1CommandsFake,
  private val accountConfigService: AccountConfigService,
  @W3 private val fakeHardwareKeyStore: FakeHardwareKeyStore,
  @W3 private val fakeHardwareSpendingWalletProvider: FakeHardwareSpendingWalletProvider,
  private val fakeHardwareStatesDao: FakeHardwareStatesDao,
  private val messageSigner: MessageSigner,
  private val signatureUtils: SignatureUtils,
) : NfcCommands by w1CommandsFake {
  /**
   * Creates a standard [HardwareInteraction.ConfirmWithEmulatedPrompt] with Approve/Deny options.
   * Most W3 fake commands follow this identical pattern — this eliminates the boilerplate.
   *
   * @param details Contextual info displayed in the emulated prompt sheet
   * @param onApprove Called on the second NFC tap when user approves
   * @param onDeny Called when user denies; defaults to throwing [NfcException.UserDenied]
   */
  private fun <T> emulatedPrompt(
    details: List<EmulatedPromptOption.Detail>,
    onApprove: suspend (NfcSession, NfcCommands) -> T,
    onDeny: suspend (NfcSession, NfcCommands) -> T = { _, _ -> throw NfcException.UserDenied() },
  ): HardwareInteraction.ConfirmWithEmulatedPrompt<T> =
    HardwareInteraction.ConfirmWithEmulatedPrompt(
      details = details,
      approve = EmulatedPromptOption(
        fetchResult = { session, commands ->
          HardwareInteraction.Completed(onApprove(session, commands))
        }
      ),
      deny = EmulatedPromptOption(
        fetchResult = { session, commands ->
          HardwareInteraction.Completed(onDeny(session, commands))
        }
      )
    )

  // ---- Per-device state (independent from W1) ----

  private var fingerprintEnrollmentResult = FingerprintEnrollmentResult(
    status = FingerprintEnrollmentStatus.NOT_IN_PROGRESS,
    passCount = null,
    failCount = null,
    diagnostics = null
  )
  private var enrolledFingerprints =
    EnrolledFingerprints(
      fingerprintHandles = listOf(
        FingerprintHandle(
          index = EnrolledFingerprints.FIRST_FINGERPRINT_INDEX,
          label = ""
        )
      )
    )

  private fun presentedHardwareType(): HardwareType =
    accountConfigService.defaultConfig().value.hardwareType ?: HardwareType.W1

  /**
   * Tactical fake-only workaround for cloud restore flows that always request W3 commands.
   *
   * When the sealed CSEKs were clearly produced by one fake key store, use that hardware type
   * for the whole restoration attempt. This keeps the workaround centralized to the W3 restore
   * composite and avoids relying on account/default config for this one flow.
   *
   * If the envelopes are mixed or unparseable, fall back to the currently configured fake
   * hardware type.
   */
  private suspend fun cloudBackupRestorationHardwareType(
    sealedCseks: List<SealedData>,
  ): HardwareType {
    val configuredHardwareType = presentedHardwareType()
    val w1AuthKeyBytes = w1CommandsFake.fakeHardwareKeyStore.getAuthKeypair().privateKey.key.bytes
    val w3AuthKeyBytes = fakeHardwareKeyStore.getAuthKeypair().privateKey.key.bytes
    val w1Nonce = w1AuthKeyBytes.substring(0, 12)
    val w1Tag = w1AuthKeyBytes.substring(12, 28)
    val w3Nonce = w3AuthKeyBytes.substring(0, 12)
    val w3Tag = w3AuthKeyBytes.substring(12, 28)

    val matchedHardwareTypes = sealedCseks.mapNotNull { sealedCsek ->
      val parsedSealedData = runCatching {
        FakeSealedDataCodec.parseSealedDataProto(sealedCsek)
      }.getOrNull() ?: return configuredHardwareType

      val matchesW1 = parsedSealedData.nonce == w1Nonce && parsedSealedData.tag == w1Tag
      val matchesW3 = parsedSealedData.nonce == w3Nonce && parsedSealedData.tag == w3Tag

      when {
        matchesW1 && !matchesW3 -> HardwareType.W1
        matchesW3 && !matchesW1 -> HardwareType.W3
        else -> return configuredHardwareType
      }
    }.toSet()

    return matchedHardwareTypes.singleOrNull() ?: configuredHardwareType
  }

  // ---- Shared command overrides ----

  override suspend fun getAuthenticationKey(session: NfcSession) =
    when (presentedHardwareType()) {
      HardwareType.W1 -> w1CommandsFake.getAuthenticationKey(session)
      HardwareType.W3 -> HwAuthPublicKey(fakeHardwareKeyStore.getAuthKeypair().publicKey.pubKey)
    }

  override suspend fun sealData(
    session: NfcSession,
    unsealedData: ByteString,
  ): SealedData =
    when (presentedHardwareType()) {
      HardwareType.W1 -> w1CommandsFake.sealData(session, unsealedData)
      HardwareType.W3 -> FakeSealedDataCodec.sealWithKeyStore(fakeHardwareKeyStore, unsealedData)
    }

  override suspend fun unsealData(
    session: NfcSession,
    sealedData: SealedData,
  ): ByteString =
    when (presentedHardwareType()) {
      HardwareType.W1 -> w1CommandsFake.unsealData(session, sealedData)
      HardwareType.W3 -> FakeSealedDataCodec.unsealWithKeyStore(fakeHardwareKeyStore, sealedData)
    }

  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = when (presentedHardwareType()) {
    HardwareType.W1 -> w1CommandsFake.getInitialSpendingKey(session, network)
    HardwareType.W3 ->
      HwSpendingPublicKey(fakeHardwareKeyStore.getInitialSpendingKeypair(network).publicKey.key)
  }

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ) = when (presentedHardwareType()) {
    HardwareType.W1 -> w1CommandsFake.getNextSpendingKey(session, existingDescriptorPublicKeys, network)
    HardwareType.W3 ->
      HwSpendingPublicKey(
        fakeHardwareKeyStore.getNextSpendingKeypair(
          existingDescriptorPublicKeys.map { it.key.dpub },
          network
        ).publicKey.key
      )
  }

  override suspend fun getGrantRequest(
    session: NfcSession,
    action: GrantAction,
  ): GrantRequest = buildFakeGrantRequest(
    keyStore = fakeHardwareKeyStore,
    deviceSerial = FakeW3FirmwareDeviceInfo.serial,
    action = action,
    messageSigner = messageSigner,
    signatureUtils = signatureUtils
  )

  // ---- Fingerprint state overrides (use W3's own state) ----

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

  override suspend fun getEnrolledFingerprints(session: NfcSession): EnrolledFingerprints =
    enrolledFingerprints

  override suspend fun setFingerprintLabel(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ): Boolean {
    enrolledFingerprints = enrolledFingerprints.insertOrUpdateFingerprintHandle(fingerprintHandle)
    return true
  }

  override suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ): Boolean {
    enrolledFingerprints = enrolledFingerprints.insertOrUpdateFingerprintHandle(fingerprintHandle)
    fingerprintEnrollmentResult.status = FingerprintEnrollmentStatus.COMPLETE
    return true
  }

  // ---- W3-specific behavior overrides ----

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ): String {
    throw NfcException.FeatureNotSupported()
  }

  /**
   * Tracks the target version for each MCU role during FWUP.
   * Set by [fwupStart], applied by [fwupFinish] to simulate the device updating its firmware.
   */
  private val pendingMcuVersions = mutableMapOf<McuRole, String>()
  private val appliedMcuVersions = mutableMapOf<McuRole, String>()

  /**
   * Whether the hardware descriptor has been delivered to this fake device.
   * Set to true by [verifyKeysAndBuildDescriptor]. Commands that require the descriptor
   * ([getAddress], [signTransaction]) throw [NfcException.DescriptorNotLoaded] when false.
   *
   * Defaults to false, matching real W3 hardware behavior: the device starts without a
   * descriptor and requires [verifyKeysAndBuildDescriptor] to be called before signing
   * or address operations work.
   */
  private var descriptorLoaded: Boolean = false

  /**
   * Records deferCommit values passed to [fwupStart] for each MCU, for test assertions.
   */
  val fwupStartDeferCommitCalls = mutableMapOf<McuRole, Boolean>()

  /**
   * W3 hardware requires on-device confirmation for wipe operations.
   *
   * Returns [HardwareInteraction.ConfirmWithEmulatedPrompt] to simulate the device's
   * two-tap confirmation flow:
   * 1. First tap sends wipe command → firmware returns ConfirmationPending
   * 2. User confirms on device (simulated by prompt selection)
   * 3. Second tap retrieves confirmation result → firmware wipes and returns success
   */
  /** Convenience for tests that need to simulate a wiped device without an NFC session. */
  suspend fun wipeDevice() {
    fakeHardwareKeyStore.clear()
    fingerprintEnrollmentResult.status = FingerprintEnrollmentStatus.NOT_IN_PROGRESS
    descriptorLoaded = false
  }

  override suspend fun wipeDevice(session: NfcSession): HardwareInteraction<Boolean> =
    emulatedPrompt(
      details = listOf(EmulatedPromptOption.Detail("Action", "Wipe Device")),
      onApprove = { _, _ ->
        wipeDevice()
        true
      },
      onDeny = { _, _ -> false }
    )

  /**
   * W3 hardware requires on-device confirmation for firmware update operations.
   *
   * Returns [HardwareInteraction.ConfirmWithEmulatedPrompt] to simulate the device's
   * confirmation screen before starting the FWUP process.
   */
  override suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
    mcuRole: McuRole,
    version: String,
    deferCommit: Boolean,
  ): HardwareInteraction<Boolean> {
    pendingMcuVersions[mcuRole] = version
    fwupStartDeferCommitCalls[mcuRole] = deferCommit
    return emulatedPrompt(
      details = listOf(
        EmulatedPromptOption.Detail("Action", "Firmware Update"),
        EmulatedPromptOption.Detail("MCU Role", mcuRole.name),
        EmulatedPromptOption.Detail("Target Version", version),
        EmulatedPromptOption.Detail("Mode", fwupMode.name)
      ),
      onApprove = { _, _ -> true },
      onDeny = { _, _ ->
        pendingMcuVersions.remove(mcuRole)
        throw NfcException.UserDenied()
      }
    )
  }

  override suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole,
  ): FwupFinishResponseStatus {
    // Simulate the device applying the firmware update by moving the pending version
    // to applied. getDeviceInfo will reflect the new version.
    pendingMcuVersions.remove(mcuRole)?.let { appliedMcuVersions[mcuRole] = it }
    return FwupFinishResponseStatus.Success
  }

  /**
   * Override to return W3-specific device info with W3 hardware revision.
   * Reflects any firmware versions updated via [fwupFinish].
   */
  override suspend fun getDeviceInfo(session: NfcSession): FirmwareDeviceInfo =
    when (presentedHardwareType()) {
      HardwareType.W1 -> w1CommandsFake.getDeviceInfo(session)
      HardwareType.W3 ->
        if (appliedMcuVersions.isEmpty()) {
          FakeW3FirmwareDeviceInfo
        } else {
          FakeW3FirmwareDeviceInfo.copy(
            mcuInfo = FakeW3FirmwareDeviceInfo.mcuInfo.map { info ->
              appliedMcuVersions[info.mcuRole]?.let { info.copy(firmwareVersion = it) } ?: info
            }
          )
        }
    }

  /**
   * W3 hardware generates address from stored descriptor and displays it on screen.
   *
   * The address verification UI is handled at the app layer, not the NFC command layer.
   * This command simply returns the address - the user visually confirms it matches
   * what's displayed on their hardware device.
   *
   * Throws [NfcException.DescriptorNotLoaded] if [verifyKeysAndBuildDescriptor] has not
   * been called, matching real hardware behavior.
   */
  override suspend fun getAddress(
    session: NfcSession,
    addressIndex: UInt,
  ): String {
    if (!descriptorLoaded) throw NfcException.DescriptorNotLoaded()
    return "bc1q_fake_w3_$addressIndex"
  }

  /**
   * Override to return W3-specific firmware metadata with W3 hardware revision.
   */
  override suspend fun getFirmwareMetadata(
    session: NfcSession,
    mcuRole: McuRole,
  ) = FirmwareMetadata(
    activeSlot = FirmwareMetadata.FirmwareSlot.A,
    gitId = "some-fake-w3-id",
    gitBranch = "main",
    version = "1.0",
    build = "mock-w3",
    timestamp = Instant.DISTANT_PAST,
    hash = ByteString.EMPTY,
    hwRevision = "w3a-core-evt"
  )

  /**
   * W3 hardware requires on-device confirmation for transaction signing (non-PSBT protocol).
   *
   * Returns [HardwareInteraction.ConfirmWithEmulatedPrompt] to simulate the device's
   * confirmation screen. Uses W3's own spending wallet provider for signing.
   *
   * Throws [NfcException.DescriptorNotLoaded] if [verifyKeysAndBuildDescriptor] has not
   * been called, matching real hardware behavior.
   */
  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
    displayPreference: HwDisplayPreference?,
  ): HardwareInteraction<Psbt> {
    if (!descriptorLoaded) throw NfcException.DescriptorNotLoaded()
    if (fakeHardwareStatesDao.getTransactionVerificationEnabled().get() == true) {
      throw TransactionError.VerificationRequired()
    }
    return emulatedPrompt(
      details = listOf(
        EmulatedPromptOption.Detail("Action", "Sign Transaction"),
        EmulatedPromptOption.Detail(
          "Amount",
          when (displayPreference?.bitcoinDisplayUnit) {
            BitcoinDisplayUnit.Satoshi -> "₿ ${psbt.amountBtc.fractionalUnitValue}"
            else -> "${psbt.amountBtc.value.toPlainString()} BTC"
          }
        ),
        EmulatedPromptOption.Detail(
          "Fee",
          when (displayPreference?.bitcoinDisplayUnit) {
            BitcoinDisplayUnit.Satoshi -> "₿ ${psbt.fee.amount.fractionalUnitValue}"
            else -> "${psbt.fee.amount.value.toPlainString()} BTC"
          }
        )
      ),
      onApprove = { _, _ ->
        fakeHardwareSpendingWalletProvider.get(spendingKeyset)
          .signPsbt(psbt)
          .mapError { NfcException.CommandError(cause = it) }
          .getOrThrow()
      }
    )
  }

  /**
   * W3 hardware verifies keys and builds the hardware descriptor.
   *
   * This fake implementation simulates successful verification and descriptor building.
   * In a real device, this would:
   * 1. Verify the app and server keys match hardware expectations
   * 2. Verify the WSM signature over all public keys
   * 3. Store the descriptor in hardware for future use
   *
   * Sets [descriptorLoaded] to true, unblocking [getAddress] and [signTransaction].
   */
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
  ): String {
    descriptorLoaded = true
    return messageSigner
      // W3 relationship verification expects a domain-separated signature over the
      // UTF-8 hex app auth pubkey, matching firmware behavior.
      .signResult(
        ("BKRelationshipEndorsement".encodeUtf8().toByteArray() + appAuthKey.hex().encodeUtf8().toByteArray())
          .toByteString(),
        fakeHardwareKeyStore.getAuthKeypair().privateKey.key)
      .mapError { NfcException.CommandError(cause = it) }
      .getOrThrow()
  }

  /**
   * W3 hardware signs action proofs for privileged operations.
   *
   * This fake implementation emulates the two-tap confirmation flow:
   * 1. First tap returns ConfirmWithEmulatedPrompt with Approve/Deny options
   * 2. User selects an option (simulating device confirmation)
   * 3. Second tap (via fetchResult) returns the actual signature
   */
  override suspend fun signActionProof(
    session: NfcSession,
    version: UInt,
    action: ActionProofAction,
    value: String?,
    bindings: String,
  ): HardwareInteraction<String> =
    emulatedPrompt(
      details = listOfNotNull(
        EmulatedPromptOption.Detail("Action", "Sign Action Proof"),
        EmulatedPromptOption.Detail("Proof Action", action.name),
        value?.let { EmulatedPromptOption.Detail("Value", it) },
        EmulatedPromptOption.Detail("Version", version.toString())
      ),
      onApprove = { _, _ -> buildAndSignPayload(version, action, value, bindings) }
    )

  /**
   * Builds the canonical payload and signs it with the W3 fake hardware key.
   */
  private suspend fun buildAndSignPayload(
    version: UInt,
    action: ActionProofAction,
    value: String?,
    bindings: String,
  ): String {
    // Build canonical payload: ACTIONPROOF␟1␟Action␟Value␟bindings
    // where ␟ is unit separator (0x1F) per core/action-proof/src/payload.rs
    val messageToSign = Buffer().apply {
      writeUtf8("ACTIONPROOF")
      writeByte(UNIT_SEPARATOR)
      writeUtf8(version.toString())
      writeByte(UNIT_SEPARATOR)
      writeUtf8(action.toPascalCase())
      writeByte(UNIT_SEPARATOR)
      value?.let { writeUtf8(it) }
      writeByte(UNIT_SEPARATOR)
      writeUtf8(bindings)
    }.readByteString()

    val authKey = fakeHardwareKeyStore.getAuthKeypair().privateKey.key
    val derSignatureHex = messageSigner.sign(messageToSign, authKey)
    val compactSignature = signatureUtils.decodeSignatureFromDer(derSignatureHex.decodeHex())
      .toByteString()
      .hex()
    return compactSignature
  }

  /**
   * W3 hardware signs the auth challenge during lost app recovery with on-device confirmation.
   *
   * Returns [HardwareInteraction.ConfirmWithEmulatedPrompt] to simulate the two-tap flow.
   * Signs with W3's own auth key.
   */
  override suspend fun lostAppRecoverySignChallenge(
    session: NfcSession,
    challenge: ByteString,
  ): HardwareInteraction<String> =
    emulatedPrompt(
      details = listOf(
        EmulatedPromptOption.Detail("Action", "Lost App Recovery — Sign Challenge"),
        EmulatedPromptOption.Detail("Challenge", challenge.hex().take(32) + "…")
      ),
      onApprove = { _, _ ->
        messageSigner
          .signResult(challenge, fakeHardwareKeyStore.getAuthKeypair().privateKey.key)
          .mapError { NfcException.CommandError(cause = it) }
          .getOrThrow()
      }
    )

  /**
   * W3 hardware requires on-device confirmation for EEK restoration unseal.
   */
  override suspend fun eekRestorationUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ): HardwareInteraction<SymmetricKey> =
    emulatedPrompt(
      details = listOf(EmulatedPromptOption.Detail("Action", "EEK Restoration — Unseal Key")),
      onApprove = { fetchSession, _ ->
        SymmetricKeyImpl(unsealData(fetchSession, sealedKey))
      }
    )

  /**
   * W3 hardware requires on-device confirmation for cloud backup restoration.
   */
  override suspend fun <T> fullAccountCloudBackupRestoration(
    session: NfcSession,
    sealedCseks: List<SealedData>,
    onCsekUnsealed: suspend (CsekUnsealResult) -> T,
  ): HardwareInteraction<T> =
    emulatedPrompt(
      details = listOf(
        EmulatedPromptOption.Detail("Action", "Cloud Backup Restoration"),
        EmulatedPromptOption.Detail("Sealed CSEKs", sealedCseks.size.toString())
      ),
      onApprove = { fetchSession, _ ->
        val restorationHardwareType = cloudBackupRestorationHardwareType(sealedCseks)
        sealedCseks.withIndex().firstNotNullOfOrNull { (index, sealedCsek) ->
          try {
            val unsealedKey = when (restorationHardwareType) {
              HardwareType.W1 ->
                SymmetricKeyImpl(w1CommandsFake.unsealData(fetchSession, sealedCsek))
              HardwareType.W3 ->
                SymmetricKeyImpl(FakeSealedDataCodec.unsealWithKeyStore(fakeHardwareKeyStore, sealedCsek))
            }
            onCsekUnsealed(CsekUnsealResult(index = index, unsealedCsek = unsealedKey))
          } catch (_: NfcException) {
            null
          }
        } ?: throw NfcException.CommandErrorSealCsekResponseUnsealException()
      }
    )

  /**
   * W3 hardware lost app recovery composite.
   *
   * Returns [HardwareInteraction.ConfirmWithEmulatedPrompt] to simulate the two-tap flow.
   * Uses W3's own key store for unsealing, signing, and key derivation.
   */
  override suspend fun lostAppRecovery(
    session: NfcSession,
    sealedSsek: ByteString,
    onSsekUnsealed: suspend (SymmetricKey) -> LostAppRecoveryContinueParams,
  ): HardwareInteraction<LostAppRecoveryCompositeResult> =
    emulatedPrompt(
      details = listOf(EmulatedPromptOption.Detail("Action", "Lost App Recovery")),
      onApprove = { fetchSession, _ ->
        val unsealedSsek = SymmetricKeyImpl(unsealData(fetchSession, sealedSsek))
        val params = onSsekUnsealed(unsealedSsek)
        val actionProofSignatureHex = buildAndSignPayload(
          version = params.actionProofVersion,
          action = params.actionProofAction,
          value = null,
          bindings = params.actionProofBindings
        )
        val spendingKeyDpub = fakeHardwareKeyStore
          .getNextSpendingKeypair(
            existingDescriptorPublicKeys = params.existingHwSpendingKeys.map { it.key.dpub },
            network = params.network
          )
          .publicKey.key
        val authPrivateKey = fakeHardwareKeyStore.getAuthKeypair().privateKey.key
        val appAuthKeySignature = messageSigner.sign(
          params.appGlobalAuthKey.value.encodeUtf8(),
          authPrivateKey
        )
        LostAppRecoveryCompositeResult(
          actionProofSignature = actionProofSignatureHex,
          spendingKeyDpub = spendingKeyDpub,
          appAuthKeySignature = appAuthKeySignature
        )
      }
    )

  override suspend fun signChallengeAndSealSeks(
    session: NfcSession,
    challenge: ByteString,
    unsealedCsek: ByteString,
    unsealedSsek: ByteString,
  ): HardwareInteraction<SignChallengeAndSealSeksResult> =
    emulatedPrompt(
      details = listOf(
        EmulatedPromptOption.Detail("Action", "Sign Challenge & Seal SEKs"),
        EmulatedPromptOption.Detail("Challenge", challenge.hex().take(32) + "…")
      ),
      onApprove = { fetchSession, _ ->
        val signedChallenge = messageSigner
          .signResult(challenge, fakeHardwareKeyStore.getAuthKeypair().privateKey.key)
          .mapError { NfcException.CommandError(cause = it) }
          .getOrThrow()
        SignChallengeAndSealSeksResult(
          signedChallenge = signedChallenge,
          sealedCsek = sealData(fetchSession, unsealedCsek),
          sealedSsek = sealData(fetchSession, unsealedSsek)
        )
      }
    )

  /**
   * Signs both UPDATE_DESCRIPTOR_BACKUPS and ROTATE_SPENDING_KEYSET action proofs.
   * Shared by recovery authorize and upgrade authorize commands.
   */
  private suspend fun signRecoveryProofs(
    actionProofVersion: UInt,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
  ): Pair<String, String> {
    val descriptorBackupsSignature = buildAndSignPayload(
      version = actionProofVersion,
      action = ActionProofAction.UPDATE_DESCRIPTOR_BACKUPS,
      value = null,
      bindings = descriptorBackupsBindings
    )
    val activateKeysetSignature = buildAndSignPayload(
      version = actionProofVersion,
      action = ActionProofAction.ROTATE_SPENDING_KEYSET,
      value = null,
      bindings = activateKeysetBindings
    )
    return descriptorBackupsSignature to activateKeysetSignature
  }

  override suspend fun recoveryAuthorizeLostApp(
    session: NfcSession,
    sealedDdkData: SealedData?,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<RecoveryAuthorizeLostAppResult> =
    emulatedPrompt(
      details = listOf(
        EmulatedPromptOption.Detail("Action", "Recovery Authorize — Lost App"),
        EmulatedPromptOption.Detail("Proof Action", ActionProofAction.UPDATE_DESCRIPTOR_BACKUPS.name),
        EmulatedPromptOption.Detail("Proof Action", ActionProofAction.ROTATE_SPENDING_KEYSET.name),
        EmulatedPromptOption.Detail("Version", actionProofVersion.toString()),
        EmulatedPromptOption.Detail("Has DDK Data", (sealedDdkData != null).toString()),
        EmulatedPromptOption.Detail("Has SSEK", (sealedSsekForDecryption != null).toString())
      ),
      onApprove = { fetchSession, _ ->
        val (descriptorSig, keysetSig) = signRecoveryProofs(actionProofVersion, descriptorBackupsBindings, activateKeysetBindings)
        RecoveryAuthorizeLostAppResult(
          descriptorBackupsSignature = descriptorSig,
          activateKeysetSignature = keysetSig,
          unsealedDdkData = sealedDdkData?.let { unsealData(fetchSession, it) },
          unsealedSsek = sealedSsekForDecryption?.let { unsealData(fetchSession, it) }
        )
      }
    )

  override suspend fun recoveryAuthorizeLostHw(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<RecoveryAuthorizeLostHwResult> =
    emulatedPrompt(
      details = listOf(
        EmulatedPromptOption.Detail("Action", "Recovery Authorize — Lost HW"),
        EmulatedPromptOption.Detail("Proof Action", ActionProofAction.UPDATE_DESCRIPTOR_BACKUPS.name),
        EmulatedPromptOption.Detail("Proof Action", ActionProofAction.ROTATE_SPENDING_KEYSET.name),
        EmulatedPromptOption.Detail("Version", actionProofVersion.toString()),
        EmulatedPromptOption.Detail("Has DDK", (ddkPrivateKeyBytes != null).toString())
      ),
      onApprove = { fetchSession, _ ->
        val (descriptorSig, keysetSig) = signRecoveryProofs(actionProofVersion, descriptorBackupsBindings, activateKeysetBindings)
        RecoveryAuthorizeLostHwResult(
          descriptorBackupsSignature = descriptorSig,
          activateKeysetSignature = keysetSig,
          sealedDdkData = ddkPrivateKeyBytes?.let { sealData(fetchSession, it) }
        )
      }
    )

  override suspend fun upgradeAuthorizeW3(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ): HardwareInteraction<UpgradeAuthorizeW3Result> =
    emulatedPrompt(
      details = listOf(
        EmulatedPromptOption.Detail("Action", "Upgrade Authorize W3"),
        EmulatedPromptOption.Detail("Proof Action", ActionProofAction.UPDATE_DESCRIPTOR_BACKUPS.name),
        EmulatedPromptOption.Detail("Proof Action", ActionProofAction.ROTATE_SPENDING_KEYSET.name),
        EmulatedPromptOption.Detail("Version", actionProofVersion.toString())
      ),
      onApprove = { fetchSession, _ ->
        val (descriptorSig, keysetSig) = signRecoveryProofs(actionProofVersion, descriptorBackupsBindings, activateKeysetBindings)
        UpgradeAuthorizeW3Result(
          descriptorBackupsSignature = descriptorSig,
          activateKeysetSignature = keysetSig,
          sealedDdkData = sealData(fetchSession, ddkPrivateKeyBytes)
        )
      }
    )

  /**
   * Signs the account ID and app global auth key with the hardware auth key.
   * Returns (hwSignedAccountId, appGlobalAuthKeyHwSignature, hwAuthPublicKey).
   */
  private suspend fun signAuthKeyRotation(
    accountId: String,
    appGlobalAuthPublicKey: String,
  ): Triple<String, String, HwAuthPublicKey> {
    val authKeypair = fakeHardwareKeyStore.getAuthKeypair()
    val authPrivateKey = authKeypair.privateKey.key
    return Triple(
      messageSigner.sign(accountId.encodeUtf8(), authPrivateKey),
      messageSigner.sign(appGlobalAuthPublicKey.encodeUtf8(), authPrivateKey),
      HwAuthPublicKey(authKeypair.publicKey.pubKey)
    )
  }

  override suspend fun rotateAppAuthKeys(
    session: NfcSession,
    params: RotateAppAuthKeysContinueParams,
  ): HardwareInteraction<RotateAppAuthKeysCompositeResult> =
    emulatedPrompt(
      details = listOf(
        EmulatedPromptOption.Detail("Action", "Rotate App Auth Keys"),
        EmulatedPromptOption.Detail("Account ID", params.accountId)
      ),
      onApprove = { _, _ ->
        val signatureHex = buildAndSignPayload(
          version = params.actionProofVersion,
          action = params.actionProofAction,
          value = null,
          bindings = params.actionProofBindings
        )
        val (hwSignedAccountId, appAuthKeySig, hwAuthPubKey) =
          signAuthKeyRotation(params.accountId, params.appGlobalAuthPublicKey)
        RotateAppAuthKeysCompositeResult(
          actionProofSignature = signatureHex,
          hwSignedAccountId = hwSignedAccountId,
          appGlobalAuthKeyHwSignature = appAuthKeySig,
          hwAuthPublicKey = hwAuthPubKey
        )
      }
    )

  override suspend fun upgradeRotateAppAuthKeys(
    session: NfcSession,
    params: UpgradeRotateAppAuthKeysParams,
  ): HardwareInteraction<UpgradeRotateAppAuthKeysResult> =
    emulatedPrompt(
      details = listOf(
        EmulatedPromptOption.Detail("Action", "Upgrade Rotate App Auth Keys"),
        EmulatedPromptOption.Detail("Account ID", params.accountId)
      ),
      onApprove = { _, _ ->
        val (hwSignedAccountId, appAuthKeySig, hwAuthPubKey) =
          signAuthKeyRotation(params.accountId, params.appGlobalAuthPublicKey)
        UpgradeRotateAppAuthKeysResult(
          hwSignedAccountId = hwSignedAccountId,
          appGlobalAuthKeyHwSignature = appAuthKeySig,
          hwAuthPublicKey = hwAuthPubKey
        )
      }
    )
}

/**
 * Fake firmware device info for W3 hardware.
 * Key differences from W1:
 * - hwRevision uses W3 format: "w3a-core-evt" (product-mcu-stage)
 * - mcuInfo populated with CORE and UXC MCUs for multi-MCU FWUP support
 * - serial differs from W1 so the upgrade flow can distinguish devices
 */
val FakeW3FirmwareDeviceInfo = FirmwareDeviceInfo(
  version = "1.2.3",
  serial = "fakeS271serial",
  swType = "dev",
  hwRevision = "w3a-core-evt",
  activeSlot = FirmwareMetadata.FirmwareSlot.B,
  batteryCharge = 89.45,
  vCell = 1000,
  avgCurrentMa = 1234,
  batteryCycles = 1234,
  secureBootConfig = SecureBootConfig.PROD,
  timeRetrieved = 1691787589,
  bioMatchStats = null,
  mcuInfo = listOf(
    McuInfo(
      mcuRole = McuRole.CORE,
      mcuName = McuName.EFR32,
      firmwareVersion = "1.2.3"
    ),
    McuInfo(
      mcuRole = McuRole.UXC,
      mcuName = McuName.STM32U5,
      firmwareVersion = "1.2.3"
    )
  )
)
