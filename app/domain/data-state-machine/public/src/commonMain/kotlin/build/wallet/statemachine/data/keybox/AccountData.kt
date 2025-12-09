package build.wallet.statemachine.data.keybox

import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.statemachine.data.recovery.conflict.SomeoneElseIsRecoveringData

/**
 * Describes Account state in the app. This could be Customer or Trusted Contact
 * account.
 */
sealed interface AccountData {
  /**
   * Checking to see if we have an active account.
   */
  data object CheckingActiveAccountData : AccountData

  /**
   * An active Full Account is present.
   */
  sealed interface HasActiveFullAccountData : AccountData {
    /**
     *
     * A keybox associated with the Full Account.
     */
    val account: FullAccount

    data class RotatingAuthKeys(
      override val account: FullAccount,
      val pendingAttempt: PendingAuthKeyRotationAttempt,
    ) : HasActiveFullAccountData

    /**
     * An active Full Account is present.
     *
     * @property lostHardwareRecoveryData provides Hardware Recovery data for the active account.
     */
    data class ActiveFullAccountLoadedData(
      override val account: FullAccount,
    ) : HasActiveFullAccountData
  }

  /**
   * The keybox was recovering but it was canceled elsewhere, notify customer
   */
  data class NoLongerRecoveringFullAccountData(
    val canceledRecoveryLostFactor: PhysicalFactor,
  ) : AccountData

  /**
   * The current Full Account has a recovery happening elsewhere, notifying customer.
   */
  data class SomeoneElseIsRecoveringFullAccountData(
    val data: SomeoneElseIsRecoveringData,
    val fullAccountId: FullAccountId,
  ) : AccountData
}
