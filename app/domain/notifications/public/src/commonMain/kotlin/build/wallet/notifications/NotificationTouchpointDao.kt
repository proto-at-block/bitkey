package build.wallet.notifications

import bitkey.notifications.NotificationTouchpoint
import bitkey.notifications.NotificationTouchpoint.EmailTouchpoint
import bitkey.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

/**
 * Dao for sms and email notification touchpoints.
 * Note: Currently we only support 1 of each type, and the API of this dao reflects that.
 */
interface NotificationTouchpointDao {
  /**
   * Stores the given touchpoint, overwriting a touchpoint of the same type if one already exists.
   * @param touchpointId - the server id associated with this touchpoint
   * @param touchpoint - the touchpoint, either sms or email
   */
  suspend fun storeTouchpoint(touchpoint: NotificationTouchpoint): Result<Unit, Error>

  /** Returns the stored phone number touchpoint (value + server-assigned ID), if any */
  fun phoneTouchpoint(): Flow<PhoneNumberTouchpoint?>

  /** Returns the stored email touchpoint (value + server-assigned ID), if any */
  fun emailTouchpoint(): Flow<EmailTouchpoint?>

  /** Clears all stored touchpoints */
  suspend fun clear(): Result<Unit, Error>
}
