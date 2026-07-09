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
import build.wallet.crypto.SymmetricKey
import build.wallet.firmware.*
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.fwup.FwupMode
import build.wallet.grants.Grant
import build.wallet.grants.GrantAction
import build.wallet.grants.GrantRequest
import build.wallet.logging.*
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.ActionProofAction
import build.wallet.nfc.platform.ConfirmationHandles
import build.wallet.nfc.platform.ConfirmationResult
import build.wallet.nfc.platform.CsekUnsealResult
import build.wallet.nfc.platform.HardwareIdentityAwareNfcCommands
import build.wallet.nfc.platform.HwDisplayPreference
import build.wallet.nfc.platform.KeysetRepairRotateHwKeyParams
import build.wallet.nfc.platform.LostAppRecoveryContinueParams
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.RotateAppAuthKeysContinueParams
import build.wallet.nfc.platform.SweepSigningContext
import build.wallet.nfc.platform.UpgradeRotateAppAuthKeysParams
import build.wallet.nfc.platform.W3NfcCommands
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import okio.ByteString

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
      val wrapped: NfcCommands = when (commands) {
        is W3NfcCommands -> MetricsW3NfcCommands(
          w3Commands = commands,
          datadogRumMonitor = datadogRumMonitor,
          datadogTracer = datadogTracer,
          eventTracker = eventTracker
        )
        else -> MetricsNfcCommands(
          commands = commands,
          datadogRumMonitor = datadogRumMonitor,
          datadogTracer = datadogTracer,
          eventTracker = eventTracker
        )
      }
      next(session, wrapped)
    }
  }
}

private open class MetricsNfcCommands(
  protected val commands: NfcCommands,
  protected val datadogRumMonitor: DatadogRumMonitor,
  protected val datadogTracer: DatadogTracer,
  protected val eventTracker: EventTracker,
) : NfcCommands, HardwareIdentityAwareNfcCommands {
  protected suspend fun <T> measure(
    action: String,
    block: suspend () -> T,
  ): T {
    return datadogTracer.span(spanName = SPAN_NAME, resourceName = action) {
      datadogRumMonitor.startResourceLoading(SPAN_NAME, "command", "nfc:$action", emptyMap())
      catchingResult { block() }
        .onSuccess { datadogRumMonitor.stopResourceLoading(SPAN_NAME, Other, emptyMap()) }
        .onFailure { throwable ->
          datadogRumMonitor.stopResourceLoadingError(
            SPAN_NAME,
            Network,
            throwable,
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
    version: String,
    deferCommit: Boolean,
  ) = measure("fwupStart") {
    commands.fwupStart(session, patchSize, fwupMode, mcuRole, version, deferCommit)
  }

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

  override suspend fun resolvedDeviceInfo(session: NfcSession): FirmwareDeviceInfo =
    measure("getDeviceInfo") {
      (commands as? HardwareIdentityAwareNfcCommands)?.resolvedDeviceInfo(session)
        ?: commands.getDeviceInfo(session)
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

  override suspend fun getFirmwareMetadata(
    session: NfcSession,
    mcuRole: McuRole,
  ) = measure("getFirmwareMetadata") { commands.getFirmwareMetadata(session, mcuRole) }

  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = measure("getInitialSpendingKey") { commands.getInitialSpendingKey(session, network) }

  override suspend fun getInitialSpendingPublicKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = measure("getInitialSpendingPublicKey") { commands.getInitialSpendingPublicKey(session, network) }

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

  override suspend fun showConfirmationScreen(
    session: NfcSession,
    lockOnDismiss: Boolean,
  ) = measure("showConfirmationScreen") {
    commands.showConfirmationScreen(session, lockOnDismiss)
  }

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
    displayPreference: HwDisplayPreference?,
    allowUnfinalized: Boolean,
  ) = measure("signTransaction") {
    commands.signTransaction(session, psbt, spendingKeyset, displayPreference, allowUnfinalized)
  }

  override suspend fun sweepTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
    sweepContext: SweepSigningContext,
    displayPreference: HwDisplayPreference?,
  ) = commands.sweepTransaction(session, psbt, spendingKeyset, sweepContext, displayPreference)

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

  override suspend fun eekRestorationUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ) = measure("eekRestorationUnsealSymmetricKey") {
    commands.eekRestorationUnsealSymmetricKey(session, sealedKey)
  }

  override suspend fun keysetRepairUnsealSymmetricKey(
    session: NfcSession,
    sealedKey: SealedData,
  ) = measure("keysetRepairUnsealSymmetricKey") {
    commands.keysetRepairUnsealSymmetricKey(session, sealedKey)
  }

  override suspend fun keysetRepairRotateHwKey(
    session: NfcSession,
    params: KeysetRepairRotateHwKeyParams,
  ) = measure("keysetRepairRotateHwKey") {
    commands.keysetRepairRotateHwKey(session, params)
  }

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

private class MetricsW3NfcCommands(
  private val w3Commands: W3NfcCommands,
  datadogRumMonitor: DatadogRumMonitor,
  datadogTracer: DatadogTracer,
  eventTracker: EventTracker,
) : MetricsNfcCommands(w3Commands, datadogRumMonitor, datadogTracer, eventTracker),
  W3NfcCommands {
  override suspend fun signActionProof(
    session: NfcSession,
    version: UInt,
    action: ActionProofAction,
    value: String?,
    bindings: String,
  ) = measure("signActionProof") {
    w3Commands.signActionProof(session, version, action, value, bindings)
  }

  override suspend fun lostAppRecovery(
    session: NfcSession,
    sealedSsek: ByteString,
    onSsekUnsealed: suspend (SymmetricKey) -> LostAppRecoveryContinueParams,
  ) = measure("lostAppRecovery") {
    w3Commands.lostAppRecovery(session, sealedSsek, onSsekUnsealed)
  }

  override suspend fun signChallengeAndSealSeks(
    session: NfcSession,
    challenge: ByteString,
    unsealedCsek: ByteString,
    unsealedSsek: ByteString,
  ) = measure("signChallengeAndSealSeks") {
    w3Commands.signChallengeAndSealSeks(session, challenge, unsealedCsek, unsealedSsek)
  }

  override suspend fun recoveryAuthorizeLostApp(
    session: NfcSession,
    sealedDdkData: SealedData?,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ) = measure("recoveryAuthorizeLostApp") {
    w3Commands.recoveryAuthorizeLostApp(
      session,
      sealedDdkData,
      sealedSsekForDecryption,
      descriptorBackupsBindings,
      activateKeysetBindings,
      actionProofVersion
    )
  }

  override suspend fun recoveryAuthorizeLostHw(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ) = measure("recoveryAuthorizeLostHw") {
    w3Commands.recoveryAuthorizeLostHw(
      session,
      ddkPrivateKeyBytes,
      descriptorBackupsBindings,
      activateKeysetBindings,
      actionProofVersion
    )
  }

  override suspend fun upgradeAuthorizeW3(
    session: NfcSession,
    ddkPrivateKeyBytes: ByteString,
    sealedSsekForDecryption: SealedData?,
    descriptorBackupsBindings: String,
    activateKeysetBindings: String,
    actionProofVersion: UInt,
  ) = measure("upgradeAuthorizeW3") {
    w3Commands.upgradeAuthorizeW3(
      session,
      ddkPrivateKeyBytes,
      sealedSsekForDecryption,
      descriptorBackupsBindings,
      activateKeysetBindings,
      actionProofVersion
    )
  }

  override suspend fun lostAppRecoverySignChallenge(
    session: NfcSession,
    challenge: ByteString,
  ) = measure("lostAppRecoverySignChallenge") {
    w3Commands.lostAppRecoverySignChallenge(session, challenge)
  }

  override suspend fun rotateAppAuthKeys(
    session: NfcSession,
    params: RotateAppAuthKeysContinueParams,
  ) = measure("rotateAppAuthKeys") {
    w3Commands.rotateAppAuthKeys(session, params)
  }

  override suspend fun upgradeRotateAppAuthKeys(
    session: NfcSession,
    params: UpgradeRotateAppAuthKeysParams,
  ) = measure("upgradeRotateAppAuthKeys") {
    w3Commands.upgradeRotateAppAuthKeys(session, params)
  }

  override suspend fun <T> fullAccountCloudBackupRestoration(
    session: NfcSession,
    sealedCseks: List<SealedData>,
    onCsekUnsealed: suspend (CsekUnsealResult) -> T,
  ) = measure("fullAccountCloudBackupRestoration") {
    w3Commands.fullAccountCloudBackupRestoration(session, sealedCseks, onCsekUnsealed)
  }

  override suspend fun getAddress(
    session: NfcSession,
    addressIndex: UInt,
  ) = measure("getAddress") { w3Commands.getAddress(session, addressIndex) }

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
  ) = measure("verifyKeysAndBuildDescriptor") {
    w3Commands.verifyKeysAndBuildDescriptor(
      session,
      appSpendingKey,
      appSpendingKeyChaincode,
      networkMainnet,
      appAuthKey,
      serverSpendingKey,
      serverSpendingKeyChaincode,
      wsmSignature,
      accountIndex
    )
  }
}
