package build.wallet.nfc.interceptors

import bitkey.datadog.DatadogRumMonitor
import bitkey.datadog.DatadogTracer
import bitkey.datadog.ErrorSource.Network
import bitkey.datadog.ResourceType.Other
import bitkey.datadog.span
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerFingerprintScanStatsInfo
import build.wallet.analytics.v1.FingerprintScanStats
import build.wallet.analytics.v1.TemplateMatchStats
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.catchingResult
import build.wallet.crypto.SealedData
import build.wallet.firmware.*
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.fwup.FwupMode
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.logging.*
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.ConfirmationHandles
import build.wallet.nfc.platform.ConfirmationResult
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import okio.ByteString
import kotlin.contracts.InvocationKind.EXACTLY_ONCE
import kotlin.contracts.contract

private const val SPAN_NAME = "nfc"

/**
 * Collects traces with Datadog.
 */
internal fun collectMetrics(
  datadogRumMonitor: DatadogRumMonitor,
  datadogTracer: DatadogTracer,
  eventTracker: EventTracker,
) = NfcTransactionInterceptor { next ->
  { session, commands ->
    datadogTracer.span(spanName = SPAN_NAME, resourceName = "nfcTransaction-${session.parameters.nfcFlowName}") {
      next(
        session,
        MetricsNfcCommandsImpl(
          commands = commands,
          datadogRumMonitor = datadogRumMonitor,
          datadogTracer = datadogTracer,
          eventTracker = eventTracker
        )
      )
    }
  }
}

private class MetricsNfcCommandsImpl(
  private val commands: NfcCommands,
  private val datadogRumMonitor: DatadogRumMonitor,
  private val datadogTracer: DatadogTracer,
  private val eventTracker: EventTracker,
) : NfcCommands {
  private suspend fun <T> measure(
    action: String,
    block: suspend () -> T,
  ): T {
    contract { callsInPlace(block, EXACTLY_ONCE) }
    return datadogTracer.span(spanName = SPAN_NAME, resourceName = action) {
      datadogRumMonitor.startResourceLoading(SPAN_NAME, "command", "nfc:$action", emptyMap())
      catchingResult { block() }
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
    mcuRole: McuRole,
  ) = measure("fwupStart") { commands.fwupStart(session, patchSize, fwupMode, mcuRole) }

  override suspend fun fwupTransfer(
    session: NfcSession,
    sequenceId: UInt,
    fwupData: List<UByte>,
    offset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole,
  ) = measure("fwupTransfer") {
    commands.fwupTransfer(
      session,
      sequenceId,
      fwupData,
      offset,
      fwupMode,
      mcuRole
    )
  }

  override suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
    mcuRole: McuRole,
  ) = measure("fwupFinish") {
    commands.fwupFinish(
      session,
      appPropertiesOffset,
      signatureOffset,
      fwupMode,
      mcuRole
    )
  }

  override suspend fun getAuthenticationKey(session: NfcSession) =
    measure("getAuthenticationKey") { commands.getAuthenticationKey(session) }

  override suspend fun getCoredumpCount(session: NfcSession) =
    measure("getCoredumpCount") { commands.getCoredumpCount(session) }

  override suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
    mcuRole: McuRole,
  ) = measure("getCoredumpFragment") { commands.getCoredumpFragment(session, offset, mcuRole) }

  override suspend fun getDeviceInfo(session: NfcSession) =
    measure("getDeviceInfo") {
      val deviceInfo = commands.getDeviceInfo(session)

      // Store into Snowflake. We should fix all of the munge-ing.
      deviceInfo.bioMatchStats?.let { bioMatchStats ->
        eventTracker.track(
          EventTrackerFingerprintScanStatsInfo(
            stats = FingerprintScanStats(
              pass_counts = bioMatchStats.passCounts.map {
                TemplateMatchStats(
                  pass_count = it.passCount.toInt(),
                  firmware_version = it.firmwareVersion
                )
              },
              fail_count = bioMatchStats.failCount.toInt()
            )
          )
        )
      }

      deviceInfo
    }

  override suspend fun getEvents(
    session: NfcSession,
    mcuRole: McuRole,
  ) = measure("getEvents") { commands.getEvents(session, mcuRole) }

  override suspend fun getFirmwareFeatureFlags(session: NfcSession): List<FirmwareFeatureFlagCfg> =
    measure("getFirmwareFeatureFlags") { commands.getFirmwareFeatureFlags(session) }

  override suspend fun getFingerprintEnrollmentStatus(
    session: NfcSession,
    isEnrollmentContextAware: Boolean,
  ) = measure("getFingerprintEnrollmentStatus") {
    val result = commands.getFingerprintEnrollmentStatus(session, isEnrollmentContextAware)
    // Log diagnostics
    logDebug { "Fingerprint enrollment result: $result" }
    result
  }

  override suspend fun deleteFingerprint(
    session: NfcSession,
    index: Int,
  ): Boolean = measure("deleteFingerprint") { commands.deleteFingerprint(session, index) }

  override suspend fun getUnlockMethod(session: NfcSession): UnlockInfo =
    measure("getUnlockMethod") { commands.getUnlockMethod(session) }

  override suspend fun cancelFingerprintEnrollment(session: NfcSession): Boolean =
    measure("cancelFingerprintEnrollment") { commands.cancelFingerprintEnrollment(session) }

  override suspend fun getEnrolledFingerprints(session: NfcSession): EnrolledFingerprints =
    measure("getEnrolledFingerprints") { commands.getEnrolledFingerprints(session) }

  override suspend fun setFingerprintLabel(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = measure("setFingerprintLabel") { commands.setFingerprintLabel(session, fingerprintHandle) }

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

  override suspend fun sealData(
    session: NfcSession,
    unsealedData: ByteString,
  ) = measure("sealData") { commands.sealData(session, unsealedData) }

  override suspend fun unsealData(
    session: NfcSession,
    sealedData: SealedData,
  ) = measure("unsealData") { commands.unsealData(session, sealedData) }

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ) = measure("signChallenge") { commands.signChallenge(session, challenge) }

  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ) = measure("signTransaction") { commands.signTransaction(session, psbt, spendingKeyset) }

  override suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = measure("startFingerprintEnrollment") {
    commands.startFingerprintEnrollment(session, fingerprintHandle)
  }

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

  override suspend fun getGrantRequest(
    session: NfcSession,
    action: GrantAction,
  ): GrantRequest {
    // TODO: Add specific metrics for this command if needed
    return commands.getGrantRequest(session, action)
  }

  override suspend fun provideGrant(
    session: NfcSession,
    grant: Grant,
  ): Boolean {
    // TODO: Add specific metrics for this command if needed
    return commands.provideGrant(session, grant)
  }

  override suspend fun provisionAppAuthKey(
    session: NfcSession,
    appAuthKey: ByteString,
  ): Boolean =
    measure("provisionAppAuthKey") {
      commands.provisionAppAuthKey(session, appAuthKey)
    }

  override suspend fun getConfirmationResult(
    session: NfcSession,
    handles: ConfirmationHandles,
  ): ConfirmationResult =
    measure("getConfirmationResult") {
      commands.getConfirmationResult(session, handles)
    }
}
