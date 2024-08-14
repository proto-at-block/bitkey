package build.wallet.notifications

import kotlinx.coroutines.flow.MutableStateFlow

class NotificationTouchpointServiceFake : NotificationTouchpointService {
  private val defaultData = NotificationTouchpointData(
    phoneNumber = null,
    email = null
  )

  private val notificationTouchpointData: MutableStateFlow<NotificationTouchpointData> =
    MutableStateFlow(defaultData)

  override fun notificationTouchpointData() = notificationTouchpointData

  fun reset() {
    notificationTouchpointData.value = defaultData
  }
}
