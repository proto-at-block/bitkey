package build.wallet.statemachine.data.recovery.losthardware

import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData

/**
 * Describes Lost Hw DN recovery state.
 */
sealed interface LostHardwareRecoveryData {
  /**
   * Indicates that HW recovery has not started and can be initialized
   */
  object LostHardwareRecoveryNotStarted : LostHardwareRecoveryData

  /**
   * Indicates that hardware DN recovery has started and we are waiting for delay
   * or completing recovery.
   */
  data class LostHardwareRecoveryInProgressData(
    val recoveryInProgressData: RecoveryInProgressData,
  ) : LostHardwareRecoveryData
}
