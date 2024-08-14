package build.wallet.recovery.socrec

import kotlinx.coroutines.flow.Flow

/**
 * Domain service for managing Social Recovery operations.
 *
 * Currently, has minimal implementation. Majority of the logic is handled
 * by the [SocRecRelationshipsRepository], [TrustedContactKeyAuthenticator], etc.
 * The logic should eventually be implemented through this service - TODO(W-9361).
 *
 */
interface SocRecService {
  /**
   * Emits true if the Customer just completed Social Recovery through
   * a Trusted Contact, in the same app session (without closing the app).
   * At this point, the Customer needs to complete the hardware replacement, unless
   * they have found their existing hardware.
   */
  fun justCompletedRecovery(): Flow<Boolean>
}
