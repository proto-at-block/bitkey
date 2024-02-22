package build.wallet.nfc.interceptors

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.Csek
import build.wallet.datadog.DatadogRumMonitor
import build.wallet.datadog.DatadogTracer
import build.wallet.datadog.ErrorSource.Network
import build.wallet.datadog.ResourceType.Other
import build.wallet.datadog.span
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.fwup.FwupMode
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import okio.ByteString
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract

private const val SPAN_NAME = "nfc"

/**
 * Collects traces with Datadog.
 */
fun collectMetrics(
  datadogRumMonitor: DatadogRumMonitor,
  datadogTracer: DatadogTracer,
) = NfcTransactionInterceptor { next ->
  { session, commands ->
    datadogTracer.span(spanName = SPAN_NAME, resourceName = "transaction") {
      next(
        session,
        MetricsNfcCommandsImpl(
          commands = commands,
          datadogRumMonitor = datadogRumMonitor,
          datadogTracer = datadogTracer
        )
      )
    }
  }
}

private class MetricsNfcCommandsImpl(
  private val commands: NfcCommands,
  private val datadogRumMonitor: DatadogRumMonitor,
  private val datadogTracer: DatadogTracer,
) : NfcCommands {
  @OptIn(ExperimentalContracts::class)
  private suspend fun <T> measure(
    action: String,
    block: suspend () -> T,
  ): T {
    contract { callsInPlace(block, EXACTLY_ONCE) }
    return datadogTracer.span(spanName = SPAN_NAME, resourceName = action) {
      datadogRumMonitor.startResourceLoading(SPAN_NAME, "command", "nfc:$action", emptyMap())
      runSuspendCatching { block() }
        .onSuccess { datadogRumMonitor.stopResourceLoading(SPAN_NAME, Other, emptyMap()) }
        .onFailure {
          datadogRumMonitor.stopResourceLoadingError(
            SPAN_NAME,
            Network,
            it,
            emptyMap()
          )
        }
        .getOrThrow()
    }
  }

  override suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
  ) = measure("fwupStart") { commands.fwupStart(session, patchSize, fwupMode) }

  override suspend fun fwupTransfer(
    session: NfcSession,
    sequenceId: UInt,
    fwupData: List<UByte>,
    offset: UInt,
    fwupMode: FwupMode,
  ) = measure("fwupTransfer") {
    commands.fwupTransfer(
      session,
      sequenceId,
      fwupData,
      offset,
      fwupMode
    )
  }

  override suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
  ) = measure("fwupFinish") {
    commands.fwupFinish(
      session,
      appPropertiesOffset,
      signatureOffset,
      fwupMode
    )
  }

  override suspend fun getAuthenticationKey(session: NfcSession) =
    measure("getAuthenticationKey") { commands.getAuthenticationKey(session) }

  override suspend fun getCoredumpCount(session: NfcSession) =
    measure("getCoredumpCount") { commands.getCoredumpCount(session) }

  override suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
  ) = measure("getCoredumpFragment") { commands.getCoredumpFragment(session, offset) }

  override suspend fun getDeviceInfo(session: NfcSession) =
    measure("getDeviceInfo") { commands.getDeviceInfo(session) }

  override suspend fun getEvents(session: NfcSession) =
    measure("getEvents") { commands.getEvents(session) }

  override suspend fun getFirmwareFeatureFlags(session: NfcSession): List<FirmwareFeatureFlagCfg> =
    measure("getFirmwareFeatureFlags") { commands.getFirmwareFeatureFlags(session) }

  override suspend fun getFingerprintEnrollmentStatus(session: NfcSession) =
    measure("getFingerprintEnrollmentStatus") { commands.getFingerprintEnrollmentStatus(session) }

  override suspend fun getFirmwareMetadata(session: NfcSession) =
    measure("getFirmwareMetadata") { commands.getFirmwareMetadata(session) }

  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = measure("getInitialSpendingKey") { commands.getInitialSpendingKey(session, network) }

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ) = measure("getNextSpendingKey") {
    commands.getNextSpendingKey(
      session,
      existingDescriptorPublicKeys,
      network
    )
  }

  override suspend fun lockDevice(session: NfcSession) =
    measure("lockDevice") { commands.lockDevice(session) }

  override suspend fun queryAuthentication(session: NfcSession) =
    measure("queryAuthentication") { commands.queryAuthentication(session) }

  override suspend fun sealKey(
    session: NfcSession,
    unsealedKey: Csek,
  ) = measure("sealKey") { commands.sealKey(session, unsealedKey) }

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ) = measure("signChallenge") { commands.signChallenge(session, challenge) }

  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ) = measure("signTransaction") { commands.signTransaction(session, psbt, spendingKeyset) }

  override suspend fun startFingerprintEnrollment(session: NfcSession) =
    measure("startFingerprintEnrollment") { commands.startFingerprintEnrollment(session) }

  override suspend fun unsealKey(
    session: NfcSession,
    sealedKey: List<UByte>,
  ) = measure("unsealKey") { commands.unsealKey(session, sealedKey) }

  override suspend fun version(session: NfcSession) =
    measure("version") { commands.version(session) }

  override suspend fun wipeDevice(session: NfcSession) =
    measure("wipeDevice") { commands.wipeDevice(session) }

  override suspend fun getCert(
    session: NfcSession,
    certType: FirmwareCertType,
  ): List<UByte> = measure("getCert") { commands.getCert(session, certType) }

  override suspend fun signVerifyAttestationChallenge(
    session: NfcSession,
    deviceIdentityDer: List<UByte>,
    challenge: List<UByte>,
  ): Boolean =
    measure("signVerifyAttestationChallenge") {
      commands.signVerifyAttestationChallenge(session, deviceIdentityDer, challenge)
    }
}
