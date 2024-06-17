package build.wallet.statemachine.data.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.email.Email
import build.wallet.f8e.notifications.NotificationTouchpointF8eClient
import build.wallet.logging.logFailure
import build.wallet.notifications.NotificationTouchpointDao
import build.wallet.phonenumber.PhoneNumber
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class NotificationTouchpointDataStateMachineImpl(
  private val notificationTouchpointDao: NotificationTouchpointDao,
  private val notificationTouchpointF8eClient: NotificationTouchpointF8eClient,
) : NotificationTouchpointDataStateMachine {
  @Composable
  override fun model(props: NotificationTouchpointProps): NotificationTouchpointData {
    // When we first load this data, first refresh it from the server.
    // This way, we will always be in sync, most importantly after app recoveries.
    LaunchedEffect("refresh-notifications") {
      refreshFromF8e(props)
    }

    return NotificationTouchpointData(
      phoneNumber = rememberPhoneNumber(),
      email = rememberEmail()
    )
  }

  /**
   * Refreshes the notification touchpoints from f8e, overriding any existing
   * notification touchpoints with those returned by the server.
   */
  private suspend fun refreshFromF8e(props: NotificationTouchpointProps) {
    notificationTouchpointF8eClient.getTouchpoints(
      f8eEnvironment = props.account.config.f8eEnvironment,
      fullAccountId = props.account.accountId
    ).onSuccess { touchpoints ->
      notificationTouchpointDao.clear()
      touchpoints.forEach { touchpoint ->
        notificationTouchpointDao.storeTouchpoint(touchpoint)
          .logFailure {
            "Failed to store touchpoint from server in db with ID ${touchpoint.touchpointId}"
          }
      }
    }.onFailure {
      /**
       * Don't do anything if we fail. [NotificationTouchpointF8eClient] will log the failure,
       * and we will try to pull the touchpoints again the next time the app opens.
       */
    }
  }

  @Composable
  private fun rememberEmail(): Email? {
    return remember { notificationTouchpointDao.email() }
      .collectAsState(null).value
  }

  @Composable
  private fun rememberPhoneNumber(): PhoneNumber? {
    return remember { notificationTouchpointDao.phoneNumber() }
      .collectAsState(null).value
  }
}
