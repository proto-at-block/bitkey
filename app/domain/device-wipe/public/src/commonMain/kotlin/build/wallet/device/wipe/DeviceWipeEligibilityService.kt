package build.wallet.device.wipe

import bitkey.account.HardwareType
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.balance.BitcoinBalance
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.Result

/**
 * Determines whether a tapped hardware device can be wiped from the logged-in wipe flow.
 *
 * The UI owns NFC presentation and screen transitions. This service owns identity matching,
 * feature-flag gates, balance checks, and old-W1 safety checks.
 */
interface DeviceWipeEligibilityService {
  /**
   * Classifies a tapped device and returns the known wipe-flow outcome or failure reason.
   *
   * Some outcomes are blocking next steps, such as funds that must move before wiping.
   * This API is used by the manual device-settings wipe flow after the customer taps a specific
   * device, so it can use the tapped NFC identity as part of the safety decision.
   */
  suspend fun evaluateLoggedInDevice(
    account: FullAccount,
    tappedDevice: TappedDeviceIdentity,
  ): Result<DeviceWipeEligibility, DeviceWipeEligibilityError>

  /**
   * Records that the W3 upgrade found no old-W1 funds to sweep.
   *
   * W3 upgrade callers must persist this checkpoint before marking sweep as handled. If this
   * returns an error, the upgrade should fail closed and leave the old-W1 automatic reminder
   * ineligible.
   */
  suspend fun recordW3UpgradeSweepNotRequired(): Result<Unit, Error>

  /**
   * Records the broadcast transaction ids for W3-upgrade sweeps.
   *
   * Callers must pass the complete non-empty set of txids returned from successful broadcast calls.
   * Persistence failure after broadcast must fail the sweep state before it is marked handled, so
   * the automatic old-W1 reminder remains ineligible until the app can safely record confirmation
   * requirements. Replaces any previously recorded txids so retry attempts do not leave stale
   * confirmation requirements behind.
   */
  suspend fun recordW3UpgradeSweepTxids(txids: Set<String>): Result<Unit, Error>

  /**
   * Returns whether the W3 upgrade has persisted evidence that an old-W1 sweep was attempted.
   *
   * This must be derived from local persisted sweep state, not UI memory, so resumed W3 upgrade
   * flows do not overwrite a recorded sweep with a no-sweep-required checkpoint.
   */
  suspend fun hasW3UpgradeSweepAttempted(): Result<Boolean, Error>

  /**
   * Returns whether the old W1 from a W3 account is ready to wipe without another identity tap.
   *
   * This is only for the automatic app-open reminder. It intentionally requires persisted W3
   * upgrade history, a known historical W1 fingerprint, no dismissed reminder, no funds left on
   * the historical W1, and either a no-sweep-required checkpoint or enough confirmations for every
   * tracked sweep txid. Unknown sweep status is not eligible here.
   *
   * Manual wipe still goes through [evaluateLoggedInDevice] after the customer taps a device.
   */
  suspend fun oldW1WipeReadiness(
    account: FullAccount,
  ): Result<OldW1WipeReadiness, DeviceWipeEligibilityError>

  /**
   * Marks the app-open old-W1 wipe reminder as dismissed for this local W3 upgrade state.
   *
   * Dismissal only suppresses the automatic reminder. The customer can still start the manual wipe
   * flow from device settings, where the tapped device identity is checked again.
   */
  suspend fun markOldW1WipeReminderDismissed(): Result<Unit, Error>

  /**
   * Records that a successfully wiped inactive device is the old W1 from W3 upgrade history.
   *
   * This is intentionally a no-op for all other inactive devices. It is called after a successful
   * manual wipe so customers who wipe the tracked old W1 before seeing the automatic reminder
   * do not see that reminder later.
   */
  suspend fun recordW3UpgradeOldW1WipedIfApplicable(
    account: FullAccount,
    device: InactiveHardwareDevice,
  ): Result<Unit, Error>

  /**
   * Performs the final inactive-device identity check immediately before issuing the destructive wipe.
   *
   * The tapped device must have [expectedDevice.hardwareType], and the tapped identity must match
   * [expectedDevice.hardwareFingerprint]. If the inactive device is the old W1 from a W3 upgrade,
   * the method also reruns the old-W1 funds and sweep-readiness safety gate so a wipe cannot
   * proceed on stale UI state.
   */
  suspend fun validateInactiveDeviceForWipe(
    account: FullAccount?,
    session: NfcSession,
    commands: NfcCommands,
    expectedDevice: InactiveHardwareDevice,
    bitcoinNetworkType: BitcoinNetworkType?,
  ): Result<Unit, InactiveDeviceWipeValidationError>
}

/**
 * Identity data gathered from the initial NFC classification tap.
 *
 * [authKey] is present when the device can sign the auth-key challenge.
 * [initialSpendingKeyFingerprint] is present when the UI was able to read the initial spending key
 * for the active Bitcoin network.
 */
data class TappedDeviceIdentity(
  val deviceInfo: FirmwareDeviceInfo,
  val authKey: HwAuthPublicKey?,
  val initialSpendingKeyFingerprint: String?,
)

/**
 * Inactive hardware identity used by manual wipe execution.
 *
 * [hardwareType] controls NFC behavior. [hardwareFingerprint] is the initial hardware
 * spending-key fingerprint and is the only inactive-device identity used for wipe eligibility.
 */
data class InactiveHardwareDevice(
  val hardwareType: HardwareType,
  val hardwareFingerprint: String,
)

/**
 * Automatic old-W1 wipe reminder readiness.
 */
sealed interface OldW1WipeReadiness {
  /**
   * The automatic reminder may be shown for [device].
   */
  data class Ready(
    val device: InactiveHardwareDevice,
  ) : OldW1WipeReadiness

  /**
   * The automatic reminder must not be shown.
   */
  data object NotReady : OldW1WipeReadiness
}

/**
 * Result of classifying a tapped device in the manual logged-in wipe flow.
 */
sealed interface DeviceWipeEligibility {
  /**
   * The tapped device is the currently paired hardware and has no funds blocking wipe.
   */
  data object ActiveReady : DeviceWipeEligibility

  /**
   * The tapped device is the currently paired hardware, but its active wallet still has funds.
   */
  data class ActiveHasFunds(
    val balance: BitcoinBalance,
  ) : DeviceWipeEligibility

  /**
   * The tapped device was previously paired with this account and has no funds blocking wipe.
   */
  data class InactiveReady(
    val device: InactiveHardwareDevice,
  ) : DeviceWipeEligibility

  /**
   * The tapped device was previously paired with this account, but it still has funds.
   */
  data class InactiveHasFunds(
    val device: InactiveHardwareDevice,
  ) : DeviceWipeEligibility
}

/**
 * Recoverable reasons a tapped device cannot currently proceed through the wipe flow.
 */
sealed interface DeviceWipeEligibilityError {
  /**
   * The tapped device does not match the paired device or known historical W1 identity.
   */
  data object UnknownDevice : DeviceWipeEligibilityError

  /**
   * The app could not determine whether the currently paired wallet has funds.
   */
  data object PairedDeviceBalanceCheckFailed : DeviceWipeEligibilityError

  /**
   * The historical W1 has no sweepable funds, but active wallet transactions are still pending.
   */
  data object OldDevicePendingActiveTransaction : DeviceWipeEligibilityError

  /**
   * The historical W1 sweep has broadcast, but its transaction has not reached the confirmation threshold.
   */
  data object OldDeviceSweepPendingConfirmation : DeviceWipeEligibilityError

  /**
   * The app could not safely determine whether the historical W1 is wipe-ready.
   */
  data object OldDeviceCheckFailed : DeviceWipeEligibilityError
}

/**
 * Failure from the final validation immediately before issuing the destructive wipe command.
 */
sealed interface InactiveDeviceWipeValidationError {
  /**
   * Historical inactive-device wipe is not enabled.
   */
  data object FeatureDisabled : InactiveDeviceWipeValidationError

  /**
   * The tapped NFC device does not match the expected inactive device identity.
   */
  data object WrongDevice : InactiveDeviceWipeValidationError

  /**
   * The app needs the Bitcoin network type to identify the inactive device by spending-key fingerprint.
   */
  data object MissingBitcoinNetworkType : InactiveDeviceWipeValidationError

  /**
   * Final NFC identity or funds safety validation failed without a customer-actionable cause.
   */
  data object DeviceCheckFailed : InactiveDeviceWipeValidationError

  /**
   * The tapped inactive device is locked and must be unlocked before wipe validation can continue.
   */
  data object DeviceLocked : InactiveDeviceWipeValidationError

  /**
   * The old-W1 sweep transaction needs more confirmations before the device can be wiped.
   */
  data object OldDeviceSweepPendingConfirmation : InactiveDeviceWipeValidationError
}
