package build.wallet

import android.Manifest.permission
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.graphics.toColorInt
import build.wallet.android.platform.impl.R
import build.wallet.notification.NotificationDismissBroadcastReceiver
import build.wallet.notifications.DeviceTokenManager
import build.wallet.platform.appVariant
import build.wallet.platform.config.AppVariant.Beta
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team
import build.wallet.platform.config.TouchpointPlatform.Fcm
import build.wallet.platform.config.TouchpointPlatform.FcmCustomer
import build.wallet.platform.config.TouchpointPlatform.FcmTeam
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WalletFirebaseMessagingService : FirebaseMessagingService() {
  lateinit var deviceTokenManager: DeviceTokenManager

  lateinit var appCoroutineScope: CoroutineScope

  override fun onCreate() {
    val appComponent =
      (application as BitkeyApplication)
        .appComponent

    deviceTokenManager = appComponent.deviceTokenManager
    appCoroutineScope = appComponent.appCoroutineScope
    super.onCreate()
  }

  override fun onMessageReceived(message: RemoteMessage) {
    if (ActivityCompat.checkSelfPermission(
        this,
        permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    ) {
      message.notification?.let { notificationMessage ->
        val resultIntent =
          Intent(application, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("notification", true)
          }

        val pendingIntent =
          TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(resultIntent)
            getPendingIntent(
              0,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
          }

        val deleteIntent = Intent(this, NotificationDismissBroadcastReceiver::class.java)

        val notification =
          NotificationCompat.Builder(
            this,
            notificationMessage.channelId ?: getString(R.string.general_channel_id)
          )
            .setSmallIcon(build.wallet.android.ui.core.R.drawable.small_icon_bitkey)
            .setColor(notificationMessage.color?.toColorInt() ?: "#000000".toColorInt())
            .setPriority(
              notificationMessage.notificationPriority ?: NotificationCompat.PRIORITY_DEFAULT
            )
            .setContentTitle(notificationMessage.title)
            .setContentText(notificationMessage.body)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(
              PendingIntent.getBroadcast(
                this,
                1000,
                deleteIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
              )
            )
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this)
          .notify(1000, notification)
      }
    }
  }

  override fun onNewToken(token: String) {
    appCoroutineScope.launch {
      deviceTokenManager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = token,
        touchpointPlatform =
          when (appVariant) {
            Customer, Emergency -> FcmCustomer
            Beta -> Fcm
            Development -> FcmTeam
            Team -> FcmTeam
          }
      )
    }
  }
}
