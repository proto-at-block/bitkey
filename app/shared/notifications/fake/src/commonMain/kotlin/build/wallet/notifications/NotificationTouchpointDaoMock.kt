package build.wallet.notifications

import app.cash.turbine.Turbine
import build.wallet.db.DbError
import build.wallet.email.Email
import build.wallet.notifications.NotificationTouchpoint.EmailTouchpoint
import build.wallet.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import build.wallet.phonenumber.PhoneNumber
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationTouchpointDaoMock(
  turbine: (name: String) -> Turbine<Any>,
) : NotificationTouchpointDao {
  val clearCalls = turbine("clear touchpoint calls")
  val storeTouchpointCalls = turbine("store touchpoint calls")

  override suspend fun storeTouchpoint(touchpoint: NotificationTouchpoint): Result<Unit, DbError> {
    storeTouchpointCalls.add(touchpoint)
    when (touchpoint) {
      is PhoneNumberTouchpoint -> phoneNumberFlow.value = touchpoint.value
      is EmailTouchpoint -> emailFlow.value = touchpoint.value
    }
    return Ok(Unit)
  }

  val phoneNumberFlow = MutableStateFlow<PhoneNumber?>(null)

  override fun phoneNumber(): Flow<PhoneNumber?> = phoneNumberFlow

  val emailFlow = MutableStateFlow<Email?>(null)

  override fun email(): Flow<Email?> = emailFlow

  override suspend fun clear(): Result<Unit, DbError> {
    reset()
    clearCalls.add(Unit)
    return Ok(Unit)
  }

  fun reset() {
    phoneNumberFlow.value = null
    emailFlow.value = null
  }
}
