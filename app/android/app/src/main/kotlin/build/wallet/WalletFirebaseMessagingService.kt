package build.wallet

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.toColorInt
import build.wallet.di.AndroidAppComponent
import build.wallet.libs.platform.impl.R
import build.wallet.notification.NotificationDismissBroadcastReceiver
import build.wallet.platform.appVariant
import build.wallet.platform.config.AppVariant.*
import build.wallet.platform.config.TouchpointPlatform.FcmCustomer
import build.wallet.platform.config.TouchpointPlatform.FcmTeam
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.SecureRandom
import kotlin.uuid.ExperimentalUuidApi

class WalletFirebaseMessagingService : FirebaseMessagingService() {
  lateinit var appComponent: Deferred<AndroidAppComponent>

  override fun onCreate() {
    appComponent = (application as BitkeyApplication).appComponent
    super.onCreate()
  }

  override fun onMessageReceived(message: RemoteMessage) {
    if (ActivityCompat.checkSelfPermission(
        this,
        permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    ) {
      message.notification?.let { notificationMessage ->
        buildNotificationFromMessage(message, notificationMessage)
      }
    }
  }

  override fun onNewToken(token: String) {
    CoroutineScope(Dispatchers.Default).launch {
      appComponent.await().deviceTokenManager.addDeviceTokenIfActiveOrOnboardingAccount(
        deviceToken = token,
        touchpointPlatform = when (appVariant) {
          Customer, Emergency -> FcmCustomer
          Development -> FcmTeam
          Alpha -> FcmTeam // android doesn't use this config
          Team -> FcmTeam
        }
      )
    }
  }

  @OptIn(ExperimentalUuidApi::class)
  @SuppressLint("MissingPermission")
  private fun buildNotificationFromMessage(
    message: RemoteMessage,
    notificationMessage: RemoteMessage.Notification,
  ) {
    val resultIntent = Intent(application, MainActivity::class.java).apply {
      action = Intent.ACTION_MAIN
      addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
      val extras = message.data
      for (extra in extras) {
        putExtra(extra.key, extra.value)
      }
      putExtra("notification", true)
    }

    val pendingIntent = PendingIntent.getActivity(
      this,
      0,
      resultIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val deleteIntent = Intent(this, NotificationDismissBroadcastReceiver::class.java)

    val notification = NotificationCompat.Builder(
      this,
      notificationMessage.channelId ?: getString(R.string.general_channel_id)
    ).setSmallIcon(build.wallet.R.drawable.small_icon_bitkey)
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

    val notificationId = SecureRandom().nextInt()
    NotificationManagerCompat.from(this)
      .notify(notificationId, notification)
  }
}
