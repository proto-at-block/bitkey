package build.wallet.notifications

import app.cash.turbine.Turbine
import bitkey.notifications.NotificationTouchpoint
import bitkey.notifications.NotificationTouchpoint.EmailTouchpoint
import bitkey.notifications.NotificationTouchpoint.PhoneNumberTouchpoint
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class NotificationTouchpointDaoMock(
  turbine: (name: String) -> Turbine<Any>,
) : NotificationTouchpointDao {
  val clearCalls = turbine("clear touchpoint calls")
  val storeTouchpointCalls = turbine("store touchpoint calls")

  override suspend fun storeTouchpoint(touchpoint: NotificationTouchpoint): Result<Unit, Error> {
    storeTouchpointCalls.add(touchpoint)
    when (touchpoint) {
      is PhoneNumberTouchpoint -> {
        phoneTouchpointFlow.value = touchpoint
      }
      is EmailTouchpoint -> {
        emailTouchpointFlow.value = touchpoint
      }
    }
    return Ok(Unit)
  }

  val phoneTouchpointFlow = MutableStateFlow<PhoneNumberTouchpoint?>(null)

  override fun phoneTouchpoint(): Flow<PhoneNumberTouchpoint?> = phoneTouchpointFlow

  val emailTouchpointFlow = MutableStateFlow<EmailTouchpoint?>(null)

  override fun emailTouchpoint(): Flow<EmailTouchpoint?> = emailTouchpointFlow

  override suspend fun clear(): Result<Unit, Error> {
    reset()
    clearCalls.add(Unit)
    return Ok(Unit)
  }

  fun reset() {
    phoneTouchpointFlow.value = null
    emailTouchpointFlow.value = null
  }
}
