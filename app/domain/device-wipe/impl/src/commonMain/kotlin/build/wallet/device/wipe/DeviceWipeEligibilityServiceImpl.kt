package build.wallet.device.wipe

import bitkey.account.HardwareType.W1
import bitkey.account.HardwareType.W3
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.blockchain.BitcoinBlockchain
import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Confirmed
import build.wallet.bitcoin.transactions.BitcoinTransaction.ConfirmationStatus.Pending
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.TransactionsData
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.ensureNotNull
import build.wallet.feature.flags.WipeOldW1DeviceFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.logging.logWarn
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.verifyHardwareType
import build.wallet.recovery.sweep.SweepContext
import build.wallet.recovery.sweep.SweepService
import build.wallet.recovery.sweep.SweepService.SweepError.NoFundsToSweep
import build.wallet.recovery.sweep.SweepService.SweepError.SweepGenerationFailed
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError

/**
 * Confirmations required before a W3-upgrade sweep can make the old W1 automatically or manually
 * wipe-ready.
 */
private const val REQUIRED_W3_UPGRADE_SWEEP_CONFIRMATIONS = 5L

/**
 * Logged-in device wipe safety implementation.
 *
 * Device classification has two paths. A currently paired device is recognized by auth key first,
 * then by matching the persisted paired-device serial for the account's hardware type. An inactive
 * device is recognized only by its initial hardware spending-key fingerprint matching a non-active
 * keyset on the account.
 *
 * The automatic old-W1 reminder is stricter than the manual wipe flow: it requires persisted W3
 * history, a known old hardware fingerprint, an undismissed reminder, no sweepable old-W1 funds,
 * and a known sweep status. `UNKNOWN` sweep status is not reminder-eligible because no device was
 * tapped in that path. Manual wipe starts from a fresh NFC tap, so `UNKNOWN` can proceed for the
 * W3-upgrade old W1 when the device has no sweepable funds and there are no active-wallet pending
 * transactions.
 *
 * When W3-upgrade sweep txids are recorded, every tracked transaction must meet
 * [REQUIRED_W3_UPGRADE_SWEEP_CONFIRMATIONS]. The final wipe command path reruns NFC identity
 * validation and the inactive-device safety gate immediately before issuing the destructive
 * command, so stale UI state cannot authorize a wipe by itself.
 */
@BitkeyInject(AppScope::class)
class DeviceWipeEligibilityServiceImpl(
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
  private val bitcoinWalletService: BitcoinWalletService,
  private val bitcoinBlockchain: BitcoinBlockchain,
  private val sweepService: SweepService,
  private val w3UpgradeDeviceHistoryRepository: W3UpgradeDeviceHistoryRepository,
  private val wipeOldW1DeviceFeatureFlag: WipeOldW1DeviceFeatureFlag,
) : DeviceWipeEligibilityService {
  override suspend fun evaluateLoggedInDevice(
    account: FullAccount,
    tappedDevice: TappedDeviceIdentity,
  ): Result<DeviceWipeEligibility, DeviceWipeEligibilityError> {
    return if (isCurrentlyPairedDevice(account, tappedDevice)) {
      evaluateActiveDevice()
    } else {
      evaluateInactiveDevice(account, tappedDevice)
    }
  }

  override suspend fun recordW3UpgradeSweepNotRequired(): Result<Unit, Error> {
    return w3UpgradeDeviceHistoryRepository
      .setOldW1SweepStatus(OldW1SweepStatus.NOT_REQUIRED)
      .mapError { Error("Failed to record old-W1 sweep status", it) }
  }

  override suspend fun recordW3UpgradeSweepTxids(txids: Set<String>): Result<Unit, Error> {
    return w3UpgradeDeviceHistoryRepository
      .replaceSweepTransactions(txids)
      .mapError { Error("Failed to record W3 upgrade sweep transaction ids", it) }
  }

  override suspend fun hasW3UpgradeSweepAttempted(): Result<Boolean, Error> {
    return w3UpgradeDeviceHistoryRepository
      .getDeviceHistory()
      .map { history ->
        history?.oldW1SweepStatus == OldW1SweepStatus.CONFIRMATIONS_REQUIRED ||
          history?.sweepTxids?.isNotEmpty() == true
      }
      .mapError { Error("Failed to load W3 upgrade sweep status", it) }
  }

  override suspend fun oldW1WipeReadiness(
    account: FullAccount,
  ): Result<OldW1WipeReadiness, DeviceWipeEligibilityError> = coroutineBinding {
    if (!wipeOldW1DeviceFeatureFlag.isEnabled() || account.config.hardwareType != W3) {
      return@coroutineBinding OldW1WipeReadiness.NotReady
    }

    val deviceHistory = w3UpgradeDeviceHistoryRepository.getDeviceHistory()
      .mapError { DeviceWipeEligibilityError.OldDeviceCheckFailed }
      .bind()

    if (deviceHistory == null || deviceHistory.oldW1WipeReminderDismissed) {
      return@coroutineBinding OldW1WipeReadiness.NotReady
    }

    val oldHardwareFingerprint = deviceHistory.oldHardwareFingerprint
      ?: return@coroutineBinding OldW1WipeReadiness.NotReady

    val inactiveDevice = InactiveHardwareDevice(
      hardwareType = W1,
      hardwareFingerprint = oldHardwareFingerprint
    )

    when (
      oldW1SafetyGate(
        account = account,
        oldHardwareFingerprint = oldHardwareFingerprint,
        deviceHistory = deviceHistory,
        blockUnknownSweepStatus = true
      )
    ) {
      OldW1SafetyGateResult.Ready ->
        OldW1WipeReadiness.Ready(inactiveDevice)
      OldW1SafetyGateResult.HasFunds,
      OldW1SafetyGateResult.NotReady,
      OldW1SafetyGateResult.PendingSweepConfirmation,
      OldW1SafetyGateResult.PendingActiveTransaction,
        -> OldW1WipeReadiness.NotReady
      OldW1SafetyGateResult.CheckFailed ->
        Err(DeviceWipeEligibilityError.OldDeviceCheckFailed).bind()
    }
  }

  override suspend fun markOldW1WipeReminderDismissed(): Result<Unit, Error> {
    return w3UpgradeDeviceHistoryRepository
      .markOldW1WipeReminderDismissed()
      .mapError { Error("Failed to mark old-W1 wipe reminder dismissed", it) }
  }

  override suspend fun recordW3UpgradeOldW1WipedIfApplicable(
    account: FullAccount,
    device: InactiveHardwareDevice,
  ): Result<Unit, Error> = coroutineBinding {
    if (account.config.hardwareType != W3 || device.hardwareType != W1) {
      return@coroutineBinding
    }

    val deviceHistory = w3UpgradeDeviceHistoryRepository.getDeviceHistory()
      .mapError { Error("Failed to load W3 upgrade device history", it) }
      .bind()

    if (device.matchesW3UpgradeOldW1(account, deviceHistory)) {
      markOldW1WipeReminderDismissed().bind()
    }
  }

  override suspend fun validateInactiveDeviceForWipe(
    account: FullAccount?,
    session: NfcSession,
    commands: NfcCommands,
    expectedDevice: InactiveHardwareDevice,
    bitcoinNetworkType: BitcoinNetworkType?,
  ): Result<Unit, InactiveDeviceWipeValidationError> {
    if (!wipeOldW1DeviceFeatureFlag.isEnabled()) {
      return Err(InactiveDeviceWipeValidationError.FeatureDisabled)
    }
    val fullAccount = account ?: return Err(InactiveDeviceWipeValidationError.DeviceCheckFailed)

    return try {
      coroutineBinding {
        commands.verifyHardwareType(session, expectedType = expectedDevice.hardwareType)
        validateExpectedInactiveDeviceIdentity(
          session = session,
          commands = commands,
          expectedDevice = expectedDevice,
          bitcoinNetworkType = bitcoinNetworkType
        ).bind()
        finalInactiveDeviceReadinessCheck(fullAccount, expectedDevice).bind()
      }
    } catch (e: NfcException.WrongHardwareType) {
      logWarn {
        "Wrong hardware type during inactive device wipe validation: expected=${e.expected}, actual=${e.actual}"
      }
      Err(InactiveDeviceWipeValidationError.WrongDevice)
    } catch (e: NfcException) {
      logWarn(throwable = e) { "Unable to validate inactive device before wipe" }
      Err(InactiveDeviceWipeValidationError.DeviceCheckFailed)
    }
  }

  private suspend fun isCurrentlyPairedDevice(
    account: FullAccount,
    tappedDevice: TappedDeviceIdentity,
  ): Boolean {
    if (tappedDevice.authKey == account.keybox.activeHwKeyBundle.authKey) {
      return true
    }

    if (tappedDevice.deviceInfo.hardwareType() != account.config.hardwareType) {
      return false
    }

    return firmwareDeviceInfoDao.getDeviceInfo().get()?.serial == tappedDevice.deviceInfo.serial
  }

  private suspend fun evaluateActiveDevice(): Result<DeviceWipeEligibility, DeviceWipeEligibilityError> {
    val balance = bitcoinWalletService.transactionsData().value?.balance
      ?: return Err(DeviceWipeEligibilityError.PairedDeviceBalanceCheckFailed)

    if (balance.untrustedPending.isPositive) {
      return Ok(DeviceWipeEligibility.ActiveHasFunds(balance))
    }

    val spendingWallet = bitcoinWalletService.spendingWallet().value
    if (balance.spendable.isPositive && spendingWallet != null) {
      return spendingWallet.isBalanceSpendable()
        .fold(
          success = { isSpendable ->
            if (isSpendable) {
              Ok(DeviceWipeEligibility.ActiveHasFunds(balance))
            } else {
              Ok(DeviceWipeEligibility.ActiveReady)
            }
          },
          failure = {
            Err(DeviceWipeEligibilityError.PairedDeviceBalanceCheckFailed)
          }
        )
    }

    return Ok(DeviceWipeEligibility.ActiveReady)
  }

  private suspend fun evaluateInactiveDevice(
    account: FullAccount,
    tappedDevice: TappedDeviceIdentity,
  ): Result<DeviceWipeEligibility, DeviceWipeEligibilityError> = coroutineBinding {
    ensure(wipeOldW1DeviceFeatureFlag.isEnabled()) {
      DeviceWipeEligibilityError.UnknownDevice
    }
    val tappedFingerprint = ensureNotNull(tappedDevice.initialSpendingKeyFingerprint) {
      DeviceWipeEligibilityError.OldDeviceCheckFailed
    }
    ensure(account.matchesInactiveHardwareFingerprint(tappedFingerprint)) {
      DeviceWipeEligibilityError.UnknownDevice
    }

    val inactiveDevice = InactiveHardwareDevice(
      hardwareType = tappedDevice.deviceInfo.hardwareType(),
      hardwareFingerprint = tappedFingerprint
    )

    val deviceHistory = w3UpgradeDeviceHistoryRepository.getDeviceHistory()
      .mapError { DeviceWipeEligibilityError.OldDeviceCheckFailed }
      .bind()

    val safetyGateResult = inactiveDeviceSafetyGate(
      account = account,
      device = inactiveDevice,
      deviceHistory = deviceHistory,
      blockUnknownSweepStatus = false
    )

    when (safetyGateResult) {
      OldW1SafetyGateResult.Ready ->
        DeviceWipeEligibility.InactiveReady(inactiveDevice)
      OldW1SafetyGateResult.HasFunds ->
        DeviceWipeEligibility.InactiveHasFunds(inactiveDevice)
      OldW1SafetyGateResult.PendingActiveTransaction ->
        Err(DeviceWipeEligibilityError.OldDevicePendingActiveTransaction).bind()
      OldW1SafetyGateResult.PendingSweepConfirmation ->
        Err(DeviceWipeEligibilityError.OldDeviceSweepPendingConfirmation).bind()
      OldW1SafetyGateResult.NotReady,
      OldW1SafetyGateResult.CheckFailed,
        -> Err(DeviceWipeEligibilityError.OldDeviceCheckFailed).bind()
    }
  }

  /**
   * Shared funds and sweep-status gate for historical W1 wipe readiness.
   *
   * [blockUnknownSweepStatus] is true for the automatic app-open reminder, where the app has no
   * fresh NFC tap and must require an explicit sweep checkpoint. Manual and final wipe validation
   * pass false so a tapped old W1 with no sweepable funds can proceed when there are no active
   * pending transactions.
   */
  private suspend fun oldW1SafetyGate(
    account: FullAccount,
    oldHardwareFingerprint: String,
    deviceHistory: W3UpgradeDeviceHistory?,
    blockUnknownSweepStatus: Boolean,
  ): OldW1SafetyGateResult {
    val syncSucceeded = bitcoinWalletService.sync()
      .fold(
        success = { true },
        failure = { false }
      )
    if (!syncSucceeded) {
      return OldW1SafetyGateResult.CheckFailed
    }

    val transactionsData = bitcoinWalletService.transactionsData().value
      ?: return OldW1SafetyGateResult.CheckFailed

    return sweepService
      .estimateSweepToActiveKeyset(
        keybox = account.keybox,
        sweepContext = SweepContext.InactiveHardware(oldHardwareFingerprint)
      )
      .fold(
        success = {
          OldW1SafetyGateResult.HasFunds
        },
        failure = { error ->
          when (error) {
            NoFundsToSweep ->
              noFundsOldW1SafetyGateResult(
                transactionsData = transactionsData,
                deviceHistory = deviceHistory,
                blockUnknownSweepStatus = blockUnknownSweepStatus
              )
            is SweepGenerationFailed ->
              OldW1SafetyGateResult.CheckFailed
          }
        }
      )
  }

  private suspend fun inactiveHardwareSafetyGate(
    account: FullAccount,
    hardwareFingerprint: String,
  ): OldW1SafetyGateResult {
    return sweepService
      .estimateSweepToActiveKeyset(
        keybox = account.keybox,
        sweepContext = SweepContext.InactiveHardware(hardwareFingerprint)
      )
      .fold(
        success = {
          OldW1SafetyGateResult.HasFunds
        },
        failure = { error ->
          when (error) {
            NoFundsToSweep ->
              OldW1SafetyGateResult.Ready
            is SweepGenerationFailed ->
              OldW1SafetyGateResult.CheckFailed
          }
        }
      )
  }

  private suspend fun inactiveDeviceSafetyGate(
    account: FullAccount,
    device: InactiveHardwareDevice,
    deviceHistory: W3UpgradeDeviceHistory?,
    blockUnknownSweepStatus: Boolean,
  ): OldW1SafetyGateResult =
    if (device.matchesW3UpgradeOldW1(account, deviceHistory)) {
      oldW1SafetyGate(
        account = account,
        oldHardwareFingerprint = device.hardwareFingerprint,
        deviceHistory = deviceHistory,
        blockUnknownSweepStatus = blockUnknownSweepStatus
      )
    } else {
      inactiveHardwareSafetyGate(
        account = account,
        hardwareFingerprint = device.hardwareFingerprint
      )
    }

  private suspend fun validateExpectedInactiveDeviceIdentity(
    session: NfcSession,
    commands: NfcCommands,
    expectedDevice: InactiveHardwareDevice,
    bitcoinNetworkType: BitcoinNetworkType?,
  ): Result<Unit, InactiveDeviceWipeValidationError> {
    val network = bitcoinNetworkType
      ?: return Err(InactiveDeviceWipeValidationError.MissingBitcoinNetworkType)
    val hardwareFingerprint = try {
      commands.getInitialSpendingKey(session, network)
        .key
        .origin
        .fingerprint
    } catch (e: NfcException.CommandErrorUnauthenticated) {
      logWarn(throwable = e) { "Inactive device wipe validation requires unlocked hardware" }
      return Err(InactiveDeviceWipeValidationError.DeviceLocked)
    }

    return if (hardwareFingerprint == expectedDevice.hardwareFingerprint) {
      Ok(Unit)
    } else {
      Err(InactiveDeviceWipeValidationError.WrongDevice)
    }
  }

  private suspend fun noFundsOldW1SafetyGateResult(
    transactionsData: TransactionsData,
    deviceHistory: W3UpgradeDeviceHistory?,
    blockUnknownSweepStatus: Boolean,
  ): OldW1SafetyGateResult {
    // `UNKNOWN` means the upgrade did not record either a no-sweep-required checkpoint or
    // post-broadcast txids. The reminder treats that as not ready; manual/final paths can rely on
    // the fresh tap and active-wallet pending transaction check.
    return when (deviceHistory?.oldW1SweepStatus ?: OldW1SweepStatus.UNKNOWN) {
      OldW1SweepStatus.CONFIRMATIONS_REQUIRED ->
        trackedSweepConfirmationsReadiness(
          transactionsData = transactionsData,
          sweepTxids = deviceHistory?.sweepTxids.orEmpty()
        )
      OldW1SweepStatus.NOT_REQUIRED ->
        OldW1SafetyGateResult.Ready
      OldW1SweepStatus.UNKNOWN -> when {
        blockUnknownSweepStatus -> OldW1SafetyGateResult.NotReady
        transactionsData.hasPendingTransaction() ->
          OldW1SafetyGateResult.PendingActiveTransaction
        else -> OldW1SafetyGateResult.Ready
      }
    }
  }

  private suspend fun trackedSweepConfirmationsReadiness(
    transactionsData: TransactionsData,
    sweepTxids: Set<String>,
  ): OldW1SafetyGateResult {
    if (sweepTxids.isEmpty()) {
      return OldW1SafetyGateResult.CheckFailed
    }

    val latestBlockHeight = bitcoinBlockchain.getLatestBlockHeight()
      .fold(
        success = { it },
        failure = { return OldW1SafetyGateResult.CheckFailed }
      )

    val transactionsById = transactionsData.transactions.associateBy { it.id }
    return if (sweepTxids.all { txid ->
        transactionsById[txid]?.hasAtLeastConfirmations(latestBlockHeight) == true
      }
    ) {
      OldW1SafetyGateResult.Ready
    } else {
      OldW1SafetyGateResult.PendingSweepConfirmation
    }
  }

  private suspend fun finalInactiveDeviceReadinessCheck(
    account: FullAccount,
    expectedDevice: InactiveHardwareDevice,
  ): Result<Unit, InactiveDeviceWipeValidationError> = coroutineBinding {
    ensure(account.matchesInactiveHardwareFingerprint(expectedDevice.hardwareFingerprint)) {
      InactiveDeviceWipeValidationError.DeviceCheckFailed
    }

    val deviceHistory = w3UpgradeDeviceHistoryRepository.getDeviceHistory()
      .mapError { InactiveDeviceWipeValidationError.DeviceCheckFailed }
      .bind()

    val safetyGateResult = inactiveDeviceSafetyGate(
      account = account,
      device = expectedDevice,
      deviceHistory = deviceHistory,
      blockUnknownSweepStatus = false
    )

    when (safetyGateResult) {
      OldW1SafetyGateResult.Ready -> Unit
      OldW1SafetyGateResult.PendingSweepConfirmation ->
        Err(InactiveDeviceWipeValidationError.OldDeviceSweepPendingConfirmation).bind()
      OldW1SafetyGateResult.HasFunds,
      OldW1SafetyGateResult.PendingActiveTransaction,
      OldW1SafetyGateResult.NotReady,
      OldW1SafetyGateResult.CheckFailed,
        -> Err(InactiveDeviceWipeValidationError.DeviceCheckFailed).bind()
    }
  }

  private fun TransactionsData.hasPendingTransaction(): Boolean =
    transactions.any { it.confirmationStatus is Pending }
}

private sealed interface OldW1SafetyGateResult {
  data object Ready : OldW1SafetyGateResult

  data object HasFunds : OldW1SafetyGateResult

  data object PendingActiveTransaction : OldW1SafetyGateResult

  data object PendingSweepConfirmation : OldW1SafetyGateResult

  data object NotReady : OldW1SafetyGateResult

  data object CheckFailed : OldW1SafetyGateResult
}

private fun FullAccount.matchesInactiveHardwareFingerprint(
  fingerprint: String,
): Boolean {
  return keybox.keysets.any { keyset ->
    keyset.f8eSpendingKeyset.keysetId != keybox.activeSpendingKeyset.f8eSpendingKeyset.keysetId &&
      keyset.hardwareKey.key.origin.fingerprint == fingerprint
  }
}

private fun InactiveHardwareDevice.matchesW3UpgradeOldW1(
  account: FullAccount,
  deviceHistory: W3UpgradeDeviceHistory?,
): Boolean =
  account.config.hardwareType == W3 &&
    hardwareType == W1 &&
    deviceHistory?.oldHardwareFingerprint == hardwareFingerprint

private fun BitcoinTransaction.hasAtLeastConfirmations(latestBlockHeight: Long): Boolean {
  val confirmedBlockHeight = when (val status = confirmationStatus) {
    is Confirmed -> status.blockTime.height
    is Pending -> return false
  }

  if (latestBlockHeight < confirmedBlockHeight) {
    return false
  }

  return latestBlockHeight - confirmedBlockHeight + 1 >= REQUIRED_W3_UPGRADE_SWEEP_CONFIRMATIONS
}
