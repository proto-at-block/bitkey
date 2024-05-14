package build.wallet.nfc.interceptors

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.Csek
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareCertType
import build.wallet.firmware.FirmwareFeatureFlagCfg
import build.wallet.fwup.FwupMode
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.nfc.NfcException.CanBeRetried
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import okio.ByteString

private const val MAX_NFC_COMMAND_RETRIES = 5

/**
 * Retries NFC commands that are idempotent.
 */
fun retryCommands() =
  NfcTransactionInterceptor { next ->
    { session, commands -> next(session, RetryingNfcCommandsImpl(commands)) }
  }

/**
 * If you're here, it's probably because you're adding a new NFC command.
 *
 * This class is responsible for performing retries for our idempotent commands.
 * And almost every command is idempotent.
 *
 * Conspicuous exceptions to this rule are commands like [getEvents] and [getCoredumpFragment],
 * which download and delete data from the Bitkey hardware. Sending them multiple times,
 * without the caller knowing, would result in incorrect behaviour.
 */
private class RetryingNfcCommandsImpl(
  private val commands: NfcCommands,
) : NfcCommands {
  override suspend fun fwupStart(
    session: NfcSession,
    patchSize: UInt?,
    fwupMode: FwupMode,
  ) = retry { commands.fwupStart(session, patchSize, fwupMode) }

  override suspend fun fwupTransfer(
    session: NfcSession,
    sequenceId: UInt,
    fwupData: List<UByte>,
    offset: UInt,
    fwupMode: FwupMode,
  ) = retry { commands.fwupTransfer(session, sequenceId, fwupData, offset, fwupMode) }

  override suspend fun fwupFinish(
    session: NfcSession,
    appPropertiesOffset: UInt,
    signatureOffset: UInt,
    fwupMode: FwupMode,
  ) = retry { commands.fwupFinish(session, appPropertiesOffset, signatureOffset, fwupMode) }

  override suspend fun getAuthenticationKey(session: NfcSession) =
    retry { commands.getAuthenticationKey(session) }

  override suspend fun getCoredumpCount(session: NfcSession) =
    retry { commands.getCoredumpCount(session) }

  override suspend fun getCoredumpFragment(
    session: NfcSession,
    offset: Int,
  ) = commands.getCoredumpFragment(session, offset)

  override suspend fun getDeviceInfo(session: NfcSession) =
    retry { commands.getDeviceInfo(session) }

  override suspend fun getEvents(session: NfcSession) = commands.getEvents(session)

  override suspend fun getFirmwareFeatureFlags(session: NfcSession): List<FirmwareFeatureFlagCfg> =
    retry { commands.getFirmwareFeatureFlags(session) }

  override suspend fun getFirmwareMetadata(session: NfcSession) =
    retry { commands.getFirmwareMetadata(session) }

  override suspend fun getFingerprintEnrollmentStatus(session: NfcSession) =
    retry { commands.getFingerprintEnrollmentStatus(session) }

  override suspend fun deleteFingerprint(
    session: NfcSession,
    index: Int,
  ) = retry { commands.deleteFingerprint(session, index) }

  override suspend fun getUnlockMethod(session: NfcSession) =
    retry { commands.getUnlockMethod(session) }

  override suspend fun getEnrolledFingerprints(session: NfcSession): EnrolledFingerprints =
    retry { commands.getEnrolledFingerprints(session) }

  override suspend fun setFingerprintLabel(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = retry { commands.setFingerprintLabel(session, fingerprintHandle) }

  override suspend fun getInitialSpendingKey(
    session: NfcSession,
    network: BitcoinNetworkType,
  ) = retry { commands.getInitialSpendingKey(session, network) }

  override suspend fun getNextSpendingKey(
    session: NfcSession,
    existingDescriptorPublicKeys: List<HwSpendingPublicKey>,
    network: BitcoinNetworkType,
  ) = retry { commands.getNextSpendingKey(session, existingDescriptorPublicKeys, network) }

  override suspend fun lockDevice(session: NfcSession) = retry { commands.lockDevice(session) }

  override suspend fun queryAuthentication(session: NfcSession) =
    retry { commands.queryAuthentication(session) }

  override suspend fun sealKey(
    session: NfcSession,
    unsealedKey: Csek,
  ) = retry { commands.sealKey(session, unsealedKey) }

  override suspend fun signChallenge(
    session: NfcSession,
    challenge: ByteString,
  ) = retry { commands.signChallenge(session, challenge) }

  override suspend fun signTransaction(
    session: NfcSession,
    psbt: Psbt,
    spendingKeyset: SpendingKeyset,
  ) = retry { commands.signTransaction(session, psbt, spendingKeyset) }

  override suspend fun startFingerprintEnrollment(
    session: NfcSession,
    fingerprintHandle: FingerprintHandle,
  ) = retry { commands.startFingerprintEnrollment(session, fingerprintHandle) }

  override suspend fun unsealKey(
    session: NfcSession,
    sealedKey: List<UByte>,
  ) = retry { commands.unsealKey(session, sealedKey) }

  override suspend fun version(session: NfcSession) = retry { commands.version(session) }

  override suspend fun wipeDevice(session: NfcSession) = retry { commands.wipeDevice(session) }

  override suspend fun getCert(
    session: NfcSession,
    certType: FirmwareCertType,
  ): List<UByte> = retry { commands.getCert(session, certType) }

  override suspend fun signVerifyAttestationChallenge(
    session: NfcSession,
    deviceIdentityDer: List<UByte>,
    challenge: List<UByte>,
  ): Boolean =
    retry {
      commands.signVerifyAttestationChallenge(
        session,
        deviceIdentityDer,
        challenge
      )
    }
}

private inline fun <T> retry(block: () -> T): T {
  for (retries in 1..MAX_NFC_COMMAND_RETRIES) {
    try {
      return block()
    } catch (e: CanBeRetried) {
      if (retries >= MAX_NFC_COMMAND_RETRIES) throw e
      log(level = LogLevel.Info, tag = "NFC", throwable = e) {
        "Retrying NFC command (retry $retries / $MAX_NFC_COMMAND_RETRIES)"
      }
    }
  }
  error("NFC retries overflowed; this shouldn't be possible!")
}
