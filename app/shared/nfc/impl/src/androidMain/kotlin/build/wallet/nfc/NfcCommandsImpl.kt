package build.wallet.nfc

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.Csek
import build.wallet.core.BooleanState
import build.wallet.core.BtcNetwork
import build.wallet.core.BytesState
import build.wallet.core.CertType
import build.wallet.core.CommandException
import build.wallet.core.CoredumpFragmentState
import build.wallet.core.DescriptorPublicKeyState
import build.wallet.core.DeviceInfo
import build.wallet.core.DeviceInfoState
import build.wallet.core.EventFragmentState
import build.wallet.core.FingerprintEnrollmentStatusState
import build.wallet.core.FirmwareFeatureFlagsState
import build.wallet.core.FirmwareMetadataState
import build.wallet.core.FirmwareSlot.A
import build.wallet.core.FirmwareSlot.B
import build.wallet.core.FwupFinish
import build.wallet.core.FwupFinishRspStatus
import build.wallet.core.FwupFinishRspStatusState
import build.wallet.core.FwupStart
import build.wallet.core.FwupTransfer
import build.wallet.core.GetAuthenticationKey
import build.wallet.core.GetCert
import build.wallet.core.GetCoredumpCount
import build.wallet.core.GetCoredumpFragment
import build.wallet.core.GetDeviceInfo
import build.wallet.core.GetEvents
import build.wallet.core.GetFingerprintEnrollmentStatus
import build.wallet.core.GetFirmwareFeatureFlags
import build.wallet.core.GetFirmwareMetadata
import build.wallet.core.GetInitialSpendingKey
import build.wallet.core.GetNextSpendingKey
import build.wallet.core.LockDevice
import build.wallet.core.PartiallySignedTransactionState
import build.wallet.core.PublicKeyState
import build.wallet.core.QueryAuthentication
import build.wallet.core.SealKey
import build.wallet.core.SecureBootConfig
import build.wallet.core.SignChallenge
import build.wallet.core.SignTransaction
import build.wallet.core.SignVerifyAttestationChallenge
import build.wallet.core.SignatureState
import build.wallet.core.StartFingerprintEnrollment
import build.wallet.core.U16State
import build.wallet.core.UnsealKey
import build.wallet.core.Version
import build.wallet.core.WipeState
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.firmware.CoredumpFragment
import build.wallet.firmware.EventFragment
import build.wallet.firmware.FingerprintEnrollmentStatus.COMPLETE
import build.wallet.firmware.FingerprintEnrollmentStatus.INCOMPLETE
import build.wallet.firmware.FingerprintEnrollmentStatus.NOT_IN_PROGRESS
import build.wallet.firmware.FingerprintEnrollmentStatus.UNSPECIFIED
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.fwup.FwupMode
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.NFC_TAG
import build.wallet.logging.log
import build.wallet.nfc.platform.NfcCommands
import build.wallet.toByteString
import build.wallet.toUByteList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okio.ByteString
import build.wallet.core.CoredumpFragment as CoreCoredumpFragment
import build.wallet.core.EventFragment as CoreEventFragment
import build.wallet.core.FingerprintEnrollmentStatus as CoreFingerprintEnrollmentStatus
import build.wallet.core.FirmwareFeatureFlag as CoreFirmwareFeatureFlag
import build.wallet.core.FirmwareFeatureFlagCfg as CoreFirmwareFeatureFlagCfg
import build.wallet.core.FirmwareMetadata as CoreFirmwareMetadata

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

  override suspend fun getFingerprintEnrollmentStatus(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::GetFingerprintEnrollmentStatus,
      getNext = { command, data -> command.next(data) },
      getResponse = { state: FingerprintEnrollmentStatusState.Data -> state.response },
      generateResult = { state: FingerprintEnrollmentStatusState.Result ->
        state.value.toFingerprintEnrollmentStatus()
      }
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

  override suspend fun startFingerprintEnrollment(session: NfcSession) =
    executeCommand(
      session = session,
      generateCommand = ::StartFingerprintEnrollment,
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

private fun CoreFingerprintEnrollmentStatus.toFingerprintEnrollmentStatus() =
  when (this) {
    CoreFingerprintEnrollmentStatus.STATUS_UNSPECIFIED -> UNSPECIFIED
    CoreFingerprintEnrollmentStatus.INCOMPLETE -> INCOMPLETE
    CoreFingerprintEnrollmentStatus.COMPLETE -> COMPLETE
    CoreFingerprintEnrollmentStatus.NOT_IN_PROGRESS -> NOT_IN_PROGRESS
  }

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
    timeRetrieved = now.epochSeconds
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
    FwupMode.Normal -> build.wallet.core.FwupMode.NORMAL
    FwupMode.Delta -> build.wallet.core.FwupMode.DELTA
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

private fun CoreFirmwareFeatureFlag.toFeatureFlag() = FirmwareFeatureFlag.valueOf(name)

private fun convertFirmwareFeatureFlags(firmwareFeatureFlags: List<CoreFirmwareFeatureFlagCfg>) =
  firmwareFeatureFlags.map { FirmwareFeatureFlagCfg(it.flag.toFeatureFlag(), it.enabled) }

private fun FirmwareCertType.toCoreCertType() =
  when (this) {
    FirmwareCertType.BATCH -> CertType.BATCH_CERT
    FirmwareCertType.IDENTITY -> CertType.DEVICE_HOST_CERT
  }
