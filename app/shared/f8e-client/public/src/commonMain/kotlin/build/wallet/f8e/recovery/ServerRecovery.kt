package build.wallet.f8e.recovery

import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwAuthPublicKey
import kotlinx.datetime.Instant

/**
 * An exact copy of the server's active recovery object for the account. This object is not
 * a representation of any local attempt at recovery (see [LocalRecoveryAttempt]),
 * though in the normal use case, they should represent the same attempt. When a recovery attempt
 * is canceled, and/or contested, the two will represent different recovery attempts.
 */
data class ServerRecovery(
  /**
   * ID of remote f8e account that was used to create recovery.
   */
  val fullAccountId: FullAccountId,
  /**
   * [delayStartTime] the time at which the recovery started
   */
  val delayStartTime: Instant,
  /**
   * [delayEndTime] the time at which the recovery will end (or has ended)
   */
  val delayEndTime: Instant,
  /**
   * [lostFactor] the factor we are trying to recovery
   */
  val lostFactor: PhysicalFactor,
  /**
   * [destinationAppGlobalAuthPubKey] the app global auth pub key that will be made active on the
   * account once the recovery is complete
   */
  val destinationAppGlobalAuthPubKey: AppGlobalAuthPublicKey,
  /**
   * [destinationAppRecoveryAuthPubKey] the app recovery auth pub key that will be made active on
   * the account once the recovery is complete
   *
   * Note that this Recovery aut key is nullable. This is because it was recently introduced.
   * It's possible that we have a customer that has initiated a DN recovery without providing
   * the new Recovery Auth key (as part of older app versions). As a result, the customer
   * will be finishing Recovery nonetheless but will not end up with a newly generated Recovery
   * auth key. The Recovery auth key will be backfilled later as part of a separate process,
   * see BKR-573.
   */
  val destinationAppRecoveryAuthPubKey: AppRecoveryAuthPublicKey?,
  /**
   * [destinationHardwareAuthPubKey] the hardware auth pub key that will be made active on the
   * account once the recovery is complete
   */
  val destinationHardwareAuthPubKey: HwAuthPublicKey,
)
