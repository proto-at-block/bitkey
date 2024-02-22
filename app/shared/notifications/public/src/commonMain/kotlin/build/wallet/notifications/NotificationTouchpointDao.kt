package build.wallet.notifications

import build.wallet.db.DbError
import build.wallet.email.Email
import build.wallet.phonenumber.PhoneNumber
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
  suspend fun storeTouchpoint(touchpoint: NotificationTouchpoint): Result<Unit, DbError>

  /** Returns the stored phone number touchpoint, if any */
  fun phoneNumber(): Flow<PhoneNumber?>

  /** Returns the stored email touchpoint, if any */
  fun email(): Flow<Email?>

  /** Clears all stored touchpoints */
  suspend fun clear(): Result<Unit, DbError>
}
