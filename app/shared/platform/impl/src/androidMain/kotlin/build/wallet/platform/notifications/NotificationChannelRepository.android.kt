package build.wallet.platform.notifications

import android.app.NotificationManager
import android.content.res.Resources
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.shared.platform.impl.R

@BitkeyInject(AppScope::class)
class NotificationChannelRepository(
  resources: Resources,
  private val notificationManager: NotificationManager,
) {
  private val channels = mapOf(
    resources.getString(R.string.general_channel_id) to
      NotificationChannel(
        name = "General",
        description = "General purpose notifications for Bitkey",
        importance = NotificationManager.IMPORTANCE_DEFAULT
      ),
    resources.getString(R.string.transactions_channel_id) to
      NotificationChannel(
        name = "Transactions",
        description = "Notifications related to your transactions",
        importance = NotificationManager.IMPORTANCE_DEFAULT
      ),
    resources.getString(R.string.recovery_account_security_channel_id) to
      NotificationChannel(
        name = "Recovery & Account Security",
        description = "Notification related to account security and recovery",
        importance = NotificationManager.IMPORTANCE_DEFAULT
      ),
    resources.getString(R.string.urgent_security_channel_id) to
      NotificationChannel(
        name = "Urgent Security Updates",
        description = "Urgent security update notifications",
        importance = NotificationManager.IMPORTANCE_HIGH
      )
  )

  private val deletedChannels = listOf<ChannelId>()

  /**
   * Delete channels that are being removed and create channels that should be added
   */
  @RequiresApi(VERSION_CODES.O)
  fun setupChannels() {
    if (deletedChannels.any { id -> channels.keys.contains(id) }) {
      error("Deleted channel ids should be in the channels to list")
    }

    deleteChannels()

    channels.entries.forEach { channel ->
      android.app.NotificationChannel(
        channel.channelId(),
        channel.value.name,
        channel.value.importance
      ).apply {
        description = channel.value.description
        notificationManager.createNotificationChannel(this)
      }
    }
  }

  /**
   * Delete channels that are no longer going to be used in the app
   */
  @RequiresApi(VERSION_CODES.O)
  private fun deleteChannels() {
    deletedChannels.forEach { channelId ->
      notificationManager.deleteNotificationChannel(channelId)
    }
  }
}

typealias ChannelId = String

private fun Map.Entry<ChannelId, NotificationChannel>.channelId() = key

private data class NotificationChannel(
  val name: String,
  val description: String,
  val importance: Int,
)
