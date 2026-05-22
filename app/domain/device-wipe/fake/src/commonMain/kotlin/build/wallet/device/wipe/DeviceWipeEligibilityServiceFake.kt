package build.wallet.device.wipe

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.account.FullAccount
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class DeviceWipeEligibilityServiceFake : DeviceWipeEligibilityService {
  var evaluateLoggedInDeviceResult: Result<DeviceWipeEligibility, DeviceWipeEligibilityError> =
    Ok(DeviceWipeEligibility.ActiveReady)

  val evaluateLoggedInDeviceCalls = mutableListOf<EvaluateLoggedInDeviceCall>()

  var recordW3UpgradeSweepNotRequiredResult: Result<Unit, Error> = Ok(Unit)
  var recordW3UpgradeSweepNotRequiredCalls = 0

  var recordW3UpgradeSweepTxidsResult: Result<Unit, Error> = Ok(Unit)
  val recordW3UpgradeSweepTxidsCalls = mutableListOf<Set<String>>()

  var hasW3UpgradeSweepAttemptedResult: Result<Boolean, Error> = Ok(false)
  var hasW3UpgradeSweepAttemptedCalls = 0

  var oldW1WipeReadinessResult: Result<OldW1WipeReadiness, DeviceWipeEligibilityError> =
    Ok(OldW1WipeReadiness.NotReady)

  var markOldW1WipeReminderDismissedResult: Result<Unit, Error> = Ok(Unit)
  var markOldW1WipeReminderDismissedCalls = 0

  var recordW3UpgradeOldW1WipedIfApplicableResult: Result<Unit, Error> = Ok(Unit)
  val recordW3UpgradeOldW1WipedIfApplicableCalls =
    mutableListOf<RecordW3UpgradeOldW1WipedIfApplicableCall>()

  var validateInactiveDeviceForWipeResult: Result<Unit, InactiveDeviceWipeValidationError> =
    Ok(Unit)

  val validateInactiveDeviceForWipeCalls = mutableListOf<ValidateInactiveDeviceForWipeCall>()

  override suspend fun evaluateLoggedInDevice(
    account: FullAccount,
    tappedDevice: TappedDeviceIdentity,
  ): Result<DeviceWipeEligibility, DeviceWipeEligibilityError> {
    evaluateLoggedInDeviceCalls.add(
      EvaluateLoggedInDeviceCall(
        account = account,
        tappedDevice = tappedDevice
      )
    )
    return evaluateLoggedInDeviceResult
  }

  override suspend fun recordW3UpgradeSweepNotRequired(): Result<Unit, Error> {
    recordW3UpgradeSweepNotRequiredCalls += 1
    return recordW3UpgradeSweepNotRequiredResult
  }

  override suspend fun recordW3UpgradeSweepTxids(txids: Set<String>): Result<Unit, Error> {
    recordW3UpgradeSweepTxidsCalls.add(txids)
    return recordW3UpgradeSweepTxidsResult
  }

  override suspend fun hasW3UpgradeSweepAttempted(): Result<Boolean, Error> {
    hasW3UpgradeSweepAttemptedCalls += 1
    return hasW3UpgradeSweepAttemptedResult
  }

  override suspend fun oldW1WipeReadiness(
    account: FullAccount,
  ): Result<OldW1WipeReadiness, DeviceWipeEligibilityError> {
    return oldW1WipeReadinessResult
  }

  override suspend fun markOldW1WipeReminderDismissed(): Result<Unit, Error> {
    markOldW1WipeReminderDismissedCalls += 1
    return markOldW1WipeReminderDismissedResult
  }

  override suspend fun recordW3UpgradeOldW1WipedIfApplicable(
    account: FullAccount,
    device: InactiveHardwareDevice,
  ): Result<Unit, Error> {
    recordW3UpgradeOldW1WipedIfApplicableCalls.add(
      RecordW3UpgradeOldW1WipedIfApplicableCall(
        account = account,
        device = device
      )
    )
    return recordW3UpgradeOldW1WipedIfApplicableResult
  }

  override suspend fun validateInactiveDeviceForWipe(
    account: FullAccount?,
    session: NfcSession,
    commands: NfcCommands,
    expectedDevice: InactiveHardwareDevice,
    bitcoinNetworkType: BitcoinNetworkType?,
  ): Result<Unit, InactiveDeviceWipeValidationError> {
    validateInactiveDeviceForWipeCalls.add(
      ValidateInactiveDeviceForWipeCall(
        account = account,
        session = session,
        commands = commands,
        expectedDevice = expectedDevice,
        bitcoinNetworkType = bitcoinNetworkType
      )
    )
    return validateInactiveDeviceForWipeResult
  }

  fun reset() {
    evaluateLoggedInDeviceResult = Ok(DeviceWipeEligibility.ActiveReady)
    evaluateLoggedInDeviceCalls.clear()
    recordW3UpgradeSweepNotRequiredResult = Ok(Unit)
    recordW3UpgradeSweepNotRequiredCalls = 0
    recordW3UpgradeSweepTxidsResult = Ok(Unit)
    recordW3UpgradeSweepTxidsCalls.clear()
    hasW3UpgradeSweepAttemptedResult = Ok(false)
    hasW3UpgradeSweepAttemptedCalls = 0
    oldW1WipeReadinessResult = Ok(OldW1WipeReadiness.NotReady)
    markOldW1WipeReminderDismissedResult = Ok(Unit)
    markOldW1WipeReminderDismissedCalls = 0
    recordW3UpgradeOldW1WipedIfApplicableResult = Ok(Unit)
    recordW3UpgradeOldW1WipedIfApplicableCalls.clear()
    validateInactiveDeviceForWipeResult = Ok(Unit)
    validateInactiveDeviceForWipeCalls.clear()
  }

  data class EvaluateLoggedInDeviceCall(
    val account: FullAccount,
    val tappedDevice: TappedDeviceIdentity,
  )

  data class ValidateInactiveDeviceForWipeCall(
    val account: FullAccount?,
    val session: NfcSession,
    val commands: NfcCommands,
    val expectedDevice: InactiveHardwareDevice,
    val bitcoinNetworkType: BitcoinNetworkType?,
  )

  data class RecordW3UpgradeOldW1WipedIfApplicableCall(
    val account: FullAccount,
    val device: InactiveHardwareDevice,
  )
}
