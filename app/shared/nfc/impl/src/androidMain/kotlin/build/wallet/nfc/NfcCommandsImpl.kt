package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.Csek
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.firmware.*
import build.wallet.firmware.FingerprintEnrollmentStatus.COMPLETE
import build.wallet.firmware.FingerprintEnrollmentStatus.INCOMPLETE
import build.wallet.firmware.FingerprintEnrollmentStatus.NOT_IN_PROGRESS
import build.wallet.firmware.FingerprintEnrollmentStatus.UNSPECIFIED
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.NFC_TAG
import build.wallet.logging.log
import build.wallet.nfc.platform.NfcCommands
import build.wallet.rust.firmware.BooleanState
import build.wallet.rust.firmware.BtcNetwork
import build.wallet.rust.firmware.BytesState
import build.wallet.rust.firmware.CancelFingerprintEnrollment
import build.wallet.rust.firmware.CertType
import build.wallet.rust.firmware.CommandException
import build.wallet.rust.firmware.CoredumpFragmentState
import build.wallet.rust.firmware.DeleteFingerprint
import build.wallet.rust.firmware.DescriptorPublicKeyState
import build.wallet.rust.firmware.DeviceInfo
import build.wallet.rust.firmware.DeviceInfoState
import build.wallet.rust.firmware.EnrolledFingerprintsState
import build.wallet.rust.firmware.EventFragmentState
import build.wallet.rust.firmware.FingerprintEnrollmentResultState
import build.wallet.rust.firmware.FirmwareFeatureFlagsState
import build.wallet.rust.firmware.FirmwareMetadataState
import build.wallet.rust.firmware.FirmwareSlot.A
import build.wallet.rust.firmware.FirmwareSlot.B
import build.wallet.rust.firmware.FwupFinish
import build.wallet.rust.firmware.FwupFinishRspStatus
import build.wallet.rust.firmware.FwupFinishRspStatusState
import build.wallet.rust.firmware.FwupStart
import build.wallet.rust.firmware.FwupTransfer
import build.wallet.rust.firmware.GetAuthenticationKey
import build.wallet.rust.firmware.GetCert
import build.wallet.rust.firmware.GetCoredumpCount
import build.wallet.rust.firmware.GetCoredumpFragment
import build.wallet.rust.firmware.GetDeviceInfo
import build.wallet.rust.firmware.GetEnrolledFingerprints
import build.wallet.rust.firmware.GetEvents
import build.wallet.rust.firmware.GetFingerprintEnrollmentStatus
import build.wallet.rust.firmware.GetFirmwareFeatureFlags
import build.wallet.rust.firmware.GetFirmwareMetadata
import build.wallet.rust.firmware.GetInitialSpendingKey
import build.wallet.rust.firmware.GetNextSpendingKey
import build.wallet.rust.firmware.GetUnlockMethod
import build.wallet.rust.firmware.LockDevice
import build.wallet.rust.firmware.PartiallySignedTransactionState
import build.wallet.rust.firmware.PublicKeyState
import build.wallet.rust.firmware.QueryAuthentication
import build.wallet.rust.firmware.SealKey
import build.wallet.rust.firmware.SecureBootConfig
import build.wallet.rust.firmware.SetFingerprintLabel
import build.wallet.rust.firmware.SignChallenge
import build.wallet.rust.firmware.SignTransaction
import build.wallet.rust.firmware.SignVerifyAttestationChallenge
import build.wallet.rust.firmware.SignatureState
import build.wallet.rust.firmware.StartFingerprintEnrollment
import build.wallet.rust.firmware.U16State
import build.wallet.rust.firmware.UnlockInfoState
import build.wallet.rust.firmware.UnsealKey
import build.wallet.rust.firmware.Version
import build.wallet.rust.firmware.WipeState
import build.wallet.toByteString
import build.wallet.toUByteList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.ByteString
import build.wallet.rust.firmware.CoredumpFragment as CoreCoredumpFragment
import build.wallet.rust.firmware.EnrolledFingerprints as CoreEnrolledFingerprints
import build.wallet.rust.firmware.EventFragment as CoreEventFragment
import build.wallet.rust.firmware.FingerprintEnrollmentResult as CoreFingerprintEnrollmentResult
import build.wallet.rust.firmware.FingerprintEnrollmentStatus as CoreFingerprintEnrollmentStatus
import build.wallet.rust.firmware.FingerprintHandle as CoreFingerprintHandle
import build.wallet.rust.firmware.FirmwareFeatureFlag as CoreFirmwareFeatureFlag
import build.wallet.rust.firmware.FirmwareFeatureFlagCfg as CoreFirmwareFeatureFlagCfg
import build.wallet.rust.firmware.FirmwareMetadata as CoreFirmwareMetadata
import build.wallet.rust.firmware.UnlockInfo as CoreUnlockInfo
import build.wallet.rust.firmware.UnlockMethod as CoreUnlockMethod

class NfcCommandsImpl(
  private val clock: Clock = Clock.System,
) : NfcCommands {
  override suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
  ) = executeCommand(
    session = session,
    generateCommand = { FwupStart(patchSize, fwupMode.toCoreFwupMode()) },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: BooleanState.Data -> state.response },
    generateResult = { state: BooleanState.Result -> state.value }
  )

  override suspend fun fwupTransfer(
    session: NfcSession,
    sequenceId: UInt,
    fwupData: List<UByte>,
    offset: UInt,
    fwupMode: FwupMode,
  ) = executeCommand(
    session = session,
    generateCommand = { FwupTransfer(sequenceId, fwupData, offset, fwupMode.toCoreFwupMode()) },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: BooleanState.Data -> state.response },
    generateResult = { state: BooleanState.Result -> state.value }
  )

  override suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
  ) = executeCommand(
    session = session,
    generateCommand = {
      FwupFinish(appPropertiesOffset, signatureOffset, fwupMode.toCoreFwupMode())
    },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: FwupFinishRspStatusState.Data -> state.response },
    generateResult = { state: FwupFinishRspStatusState.Result ->
      state.value.toFwupFinishResponseStatus()
    }
  )

  override suspend fun getAuthenticationKey(session: NfcSession) =
    HwAuthPublicKey(
      Secp256k1PublicKey(
        executeCommand(
          session = session,
          generateCommand = ::GetAuthenticationKey,
          getNext = { command, data -> command.next(data) },
          getResponse = { state: PublicKeyState.Data -> state.response },
          generateResult = { state: PublicKeyState.Result -> state.value }
        )
      )
    )

  override suspend fun getCoredumpCount(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::GetCoredumpCount,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: U16State.Data -> state.response },
      generateResult = { state: U16State.Result -> state.value.toInt() }
    )

  override suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
  ) = executeCommand(
    session = session,
    generateCommand = { GetCoredumpFragment(offset.toUInt()) },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: CoredumpFragmentState.Data -> state.response },
    generateResult = { state: CoredumpFragmentState.Result ->
      state.value.toCoredumpFragment()
    }
  )

  override suspend fun getDeviceInfo(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::GetDeviceInfo,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: DeviceInfoState.Data -> state.response },
      generateResult = { state: DeviceInfoState.Result ->
        state.value.toFirmwareDeviceInfo(now = clock.now())
      }
    )

  override suspend fun getEvents(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::GetEvents,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: EventFragmentState.Data -> state.response },
      generateResult = { state: EventFragmentState.Result ->
        state.value.toEventFragment()
      }
    )

  override suspend fun getFirmwareFeatureFlags(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::GetFirmwareFeatureFlags,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: FirmwareFeatureFlagsState.Data -> state.response },
      generateResult = { state: FirmwareFeatureFlagsState.Result ->
        convertFirmwareFeatureFlags(state.value)
      }
    )

  override suspend fun getFingerprintEnrollmentStatus(
    session: NfcSession,
    isEnrollmentContextAware: Boolean,
  ) = executeCommand(
    session = session,
    generateCommand = {
      GetFingerprintEnrollmentStatus(isEnrollmentContextAware = isEnrollmentContextAware)
    },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: FingerprintEnrollmentResultState.Data -> state.response },
    generateResult = { state: FingerprintEnrollmentResultState.Result ->
      state.value.toFingerprintEnrollmentResult()
    }
  )

  override suspend fun deleteFingerprint(
    session: NfcSession,
    index: Int,
  ): Boolean =
    executeCommand(
      session = session,
      generateCommand = { DeleteFingerprint(index.toUInt()) },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: BooleanState.Data -> state.response },
      generateResult = { state: BooleanState.Result -> state.value }
    )

  override suspend fun getUnlockMethod(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::GetUnlockMethod,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: UnlockInfoState.Data -> state.response },
      generateResult = { state: UnlockInfoState.Result ->
        state.value.toUnlockInfo()
      }
    )

  override suspend fun cancelFingerprintEnrollment(session: NfcSession): Boolean =
    executeCommand(
      session = session,
      generateCommand = ::CancelFingerprintEnrollment,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: BooleanState.Data -> state.response },
      generateResult = { state: BooleanState.Result -> state.value }
    )

  override suspend fun getEnrolledFingerprints(session: NfcSession): EnrolledFingerprints =
    executeCommand(
      session = session,
      generateCommand = ::GetEnrolledFingerprints,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: EnrolledFingerprintsState.Data -> state.response },
      generateResult = { state: EnrolledFingerprintsState.Result ->
        state.value.toEnrolledFingerprints()
      }
    )

  override suspend fun setFingerprintLabel(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ): Boolean =
    executeCommand(
      session = session,
      generateCommand = {
        SetFingerprintLabel(
          fingerprintHandle.index.toUInt(),
          fingerprintHandle.label
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: BooleanState.Data -> state.response },
      generateResult = { state: BooleanState.Result -> state.value }
    )

  override suspend fun getFirmwareMetadata(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::GetFirmwareMetadata,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: FirmwareMetadataState.Data -> state.response },
      generateResult = { state: FirmwareMetadataState.Result ->
        state.value.toFirmwareMetadata()
      }
    )

  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = HwSpendingPublicKey(
    executeCommand(
      session = session,
      generateCommand = { GetInitialSpendingKey(network = network.toBtcNetwork()) },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: DescriptorPublicKeyState.Data -> state.response },
      generateResult = { state: DescriptorPublicKeyState.Result -> state.value }
    )
  )

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ) = HwSpendingPublicKey(
    executeCommand(
      session = session,
      generateCommand = {
        GetNextSpendingKey(
          existing = existingDescriptorPublicKeys.map { it.key.dpub },
          network = network.toBtcNetwork()
        )
      },
      getNext = { command, data -> command.next(data) },
      getResponse = { state: DescriptorPublicKeyState.Data -> state.response },
      generateResult = { state: DescriptorPublicKeyState.Result -> state.value }
    )
  )

  override suspend fun lockDevice(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::LockDevice,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: BooleanState.Data -> state.response },
      generateResult = { state: BooleanState.Result -> state.value }
    )

  override suspend fun queryAuthentication(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::QueryAuthentication,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: BooleanState.Data -> state.response },
      generateResult = { state: BooleanState.Result -> state.value }
    )

  override suspend fun sealKey(
    session: NfcSession,
    unsealedKey: Csek,
  ) = executeCommand(
    session = session,
    generateCommand = { SealKey(unsealedKey.key.raw.toUByteList()) },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: BytesState.Data -> state.response },
    generateResult = { state: BytesState.Result -> state.value.toByteString() }
  )

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ) = executeCommand(
    session = session,
    generateCommand = { SignChallenge(challenge.toUByteList()) },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: SignatureState.Data -> state.response },
    generateResult = { state: SignatureState.Result -> state.value }
  )

  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ) = executeCommand(
    session = session,
    generateCommand = { SignTransaction(psbt.base64) },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: PartiallySignedTransactionState.Data -> state.response },
    generateResult = { state: PartiallySignedTransactionState.Result ->
      psbt.copy(base64 = state.value)
    }
  )

  override suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = executeCommand(
    session = session,
    generateCommand = {
      StartFingerprintEnrollment(
        fingerprintHandle.index.toUInt(),
        fingerprintHandle.label
      )
    },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: BooleanState.Data -> state.response },
    generateResult = { state: BooleanState.Result -> state.value }
  )

  override suspend fun unsealKey(
    session: NfcSession,
    sealedKey: List<UByte>,
  ) = executeCommand(
    session = session,
    generateCommand = { UnsealKey(sealedKey) },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: BytesState.Data -> state.response },
    generateResult = { state: BytesState.Result -> state.value }
  )

  override suspend fun version(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::Version,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: U16State.Data -> state.response },
      generateResult = { state: U16State.Result -> state.value }
    )

  override suspend fun wipeDevice(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::WipeState,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: BooleanState.Data -> state.response },
      generateResult = { state: BooleanState.Result -> state.value }
    )

  override suspend fun getCert(
    session: NfcSession,
    certType: FirmwareCertType,
  ) = executeCommand(
    session = session,
    generateCommand = { GetCert(certType.toCoreCertType()) },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: BytesState.Data -> state.response },
    generateResult = { state: BytesState.Result -> state.value }
  )

  override suspend fun signVerifyAttestationChallenge(
    session: NfcSession,
    deviceIdentityDer: List<UByte>,
    challenge: List<UByte>,
  ) = executeCommand(
    session = session,
    generateCommand = { SignVerifyAttestationChallenge(deviceIdentityDer, challenge) },
    getNext = { command, data -> command.next(data) },
    getResponse = { state: BooleanState.Data -> state.response },
    generateResult = { state: BooleanState.Result -> state.value }
  )
}

@Suppress(
  "TooGenericExceptionCaught",
  "ThrowsCount"
) // TODO: remove when metrics is done by an observer
private suspend inline fun <
  CommandT : Any,
  StateT,
  reified DataStateT,
  reified ResultStateT,
  ReturnT,
> executeCommand(
  session: NfcSession,
  crossinline generateCommand: () -> CommandT,
  crossinline getNext: (CommandT, List<UByte>) -> StateT,
  crossinline getResponse: (DataStateT) -> List<UByte>,
  crossinline generateResult: (ResultStateT) -> ReturnT,
): ReturnT {
  val command = generateCommand()
  val commandName = command::class.simpleName
  var data = emptyList<UByte>()

  log(tag = NFC_TAG) { "NFC Command $commandName started" }

  while (true) {
    try {
      when (val state = getNext(command, data)) {
        is DataStateT -> data = session.transceive(getResponse(state))

        is ResultStateT -> {
          log(tag = NFC_TAG) { "NFC Command $commandName succeeded" }
          return generateResult(state)
        }
      }
    } catch (e: Throwable) {
      log(Warn, tag = NFC_TAG, throwable = e) { "NFC Command $commandName failed" }
      when (e) {
        is CommandException.Unauthenticated -> throw NfcException.CommandErrorUnauthenticated()
        is CommandException -> throw NfcException.CommandError(cause = e)
        else -> throw e
      }
    }
  }
}

private fun BitcoinNetworkType.toBtcNetwork() =
  when (this) {
    BitcoinNetworkType.BITCOIN -> BtcNetwork.BITCOIN
    BitcoinNetworkType.TESTNET -> BtcNetwork.TESTNET
    BitcoinNetworkType.SIGNET -> BtcNetwork.SIGNET
    BitcoinNetworkType.REGTEST -> BtcNetwork.REGTEST
  }

private fun CoreFingerprintEnrollmentResult.toFingerprintEnrollmentResult() =
  FingerprintEnrollmentResult(
    status =
      when (status) {
        CoreFingerprintEnrollmentStatus.STATUS_UNSPECIFIED -> UNSPECIFIED
        CoreFingerprintEnrollmentStatus.INCOMPLETE -> INCOMPLETE
        CoreFingerprintEnrollmentStatus.COMPLETE -> COMPLETE
        CoreFingerprintEnrollmentStatus.NOT_IN_PROGRESS -> NOT_IN_PROGRESS
      },
    passCount = passCount,
    failCount = failCount,
    diagnostics = diagnostics?.let {
      FingerprintEnrollmentDiagnostics(
        fingerCoverageValid = it.fingerCoverageValid,
        fingerCoverage = it.fingerCoverage.toInt(),
        commonModeNoiseValid = it.commonModeNoiseValid,
        commonModeNoise = it.commonModeNoise.toInt(),
        imageQualityValid = it.imageQualityValid,
        imageQuality = it.imageQuality.toInt(),
        sensorCoverageValid = it.sensorCoverageValid,
        sensorCoverage = it.sensorCoverage.toInt(),
        templateDataUpdateValid = it.templateDataUpdateValid,
        templateDataUpdate = it.templateDataUpdate.toInt()
      )
    }
  )

private fun CoreFirmwareMetadata.toFirmwareMetadata() =
  FirmwareMetadata(
    activeSlot =
      when (activeSlot) {
        A -> FirmwareSlot.A
        B -> FirmwareSlot.B
      },
    gitId = gitId,
    gitBranch = gitBranch,
    version = version,
    build = build,
    timestamp = Instant.fromEpochMilliseconds(timestamp.toLong()),
    hash = hash.toByteString(),
    hwRevision = hwRevision
  )

private fun DeviceInfo.toFirmwareDeviceInfo(now: Instant) =
  FirmwareDeviceInfo(
    version = version,
    serial = serial,
    swType = swType,
    hwRevision = hwRevision,
    activeSlot = FirmwareSlot.valueOf(activeSlot.name),
    batteryCharge = batteryCharge.toDouble(),
    vCell = vcell.toLong(),
    avgCurrentMa = avgCurrentMa.toLong(),
    batteryCycles = batteryCycles.toLong(),
    secureBootConfig =
      when (secureBootConfig) {
        null -> build.wallet.firmware.SecureBootConfig.NOT_SET
        SecureBootConfig.DEV -> build.wallet.firmware.SecureBootConfig.DEV
        SecureBootConfig.PROD -> build.wallet.firmware.SecureBootConfig.PROD
      },
    timeRetrieved = now.epochSeconds,
    bioMatchStats = bioMatchStats?.let {
      BioMatchStats(
        passCounts = it.passCounts.map { passCount ->
          TemplateMatchStats(
            passCount = passCount.passCount.toLong(),
            firmwareVersion = passCount.firmwareVersion
          )
        },
        failCount = it.failCount.toLong()
      )
    }
  )

private fun CoreEventFragment.toEventFragment() =
  EventFragment(
    fragment = fragment,
    remainingSize = remainingSize
  )

private fun CoreCoredumpFragment.toCoredumpFragment() =
  CoredumpFragment(
    data = data,
    offset = offset,
    complete = complete,
    coredumpsRemaining = coredumpsRemaining
  )

private fun FwupMode.toCoreFwupMode() =
  when (this) {
    FwupMode.Normal -> build.wallet.rust.firmware.FwupMode.NORMAL
    FwupMode.Delta -> build.wallet.rust.firmware.FwupMode.DELTA
  }

private fun FwupFinishRspStatus.toFwupFinishResponseStatus() =
  when (this) {
    FwupFinishRspStatus.ERROR -> FwupFinishResponseStatus.Error
    FwupFinishRspStatus.SIGNATURE_INVALID -> FwupFinishResponseStatus.SignatureInvalid
    FwupFinishRspStatus.SUCCESS -> FwupFinishResponseStatus.Success
    FwupFinishRspStatus.UNAUTHENTICATED -> FwupFinishResponseStatus.Unauthenticated
    FwupFinishRspStatus.UNSPECIFIED -> FwupFinishResponseStatus.Unspecified
    FwupFinishRspStatus.VERSION_INVALID -> FwupFinishResponseStatus.VersionInvalid
    FwupFinishRspStatus.WILL_APPLY_PATCH -> FwupFinishResponseStatus.WillApplyPatch
  }

private fun CoreFirmwareFeatureFlag.toFeatureFlag() =
  when (this) {
    CoreFirmwareFeatureFlag.TELEMETRY -> FirmwareFeatureFlag.TELEMETRY
    CoreFirmwareFeatureFlag.DEVICE_INFO_FLAG -> FirmwareFeatureFlag.DEVICE_INFO_FLAG
    CoreFirmwareFeatureFlag.RATE_LIMIT_TEMPLATE_UPDATE -> FirmwareFeatureFlag.RATE_LIMIT_TEMPLATE_UPDATE
    CoreFirmwareFeatureFlag.UNLOCK -> FirmwareFeatureFlag.UNLOCK
    CoreFirmwareFeatureFlag.MULTIPLE_FINGERPRINTS -> FirmwareFeatureFlag.MULTIPLE_FINGERPRINTS
    CoreFirmwareFeatureFlag.IMPROVED_FINGERPRINT_ENROLLMENT -> FirmwareFeatureFlag.IMPROVED_FINGERPRINT_ENROLLMENT
  }

private fun convertFirmwareFeatureFlags(firmwareFeatureFlags: List<CoreFirmwareFeatureFlagCfg>) =
  firmwareFeatureFlags.map { FirmwareFeatureFlagCfg(it.flag.toFeatureFlag(), it.enabled) }

private fun FirmwareCertType.toCoreCertType() =
  when (this) {
    FirmwareCertType.BATCH -> CertType.BATCH_CERT
    FirmwareCertType.IDENTITY -> CertType.DEVICE_HOST_CERT
  }

private fun CoreEnrolledFingerprints.toEnrolledFingerprints() =
  EnrolledFingerprints(
    maxCount = maxCount.toInt(),
    fingerprintHandles = fingerprints.map { it.toFingerprintHandle() }
  )

private fun CoreFingerprintHandle.toFingerprintHandle() =
  FingerprintHandle(
    index = index.toInt(),
    label = label
  )

private fun CoreUnlockInfo.toUnlockInfo() =
  UnlockInfo(
    unlockMethod = method.toUnlockMethod(),
    fingerprintIdx = fingerprintIndex?.toInt()
  )

private fun CoreUnlockMethod.toUnlockMethod() =
  when (this) {
    CoreUnlockMethod.UNSPECIFIED -> UnlockMethod.UNSPECIFIED
    CoreUnlockMethod.BIOMETRICS -> UnlockMethod.BIOMETRICS
    CoreUnlockMethod.UNLOCK_SECRET -> UnlockMethod.UNLOCK_SECRET
  }
