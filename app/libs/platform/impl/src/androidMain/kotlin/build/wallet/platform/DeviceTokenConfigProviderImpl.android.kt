package build.wallet.platform

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logWarn
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.*
import build.wallet.platform.config.DeviceTokenConfig
import build.wallet.platform.config.DeviceTokenConfigProvider
import build.wallet.platform.config.TouchpointPlatform.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@BitkeyInject(AppScope::class)
class DeviceTokenConfigProviderImpl(
  private val appVariant: AppVariant,
) : DeviceTokenConfigProvider {
  override suspend fun config(): DeviceTokenConfig? {
    val token = getToken() ?: return null
    return DeviceTokenConfig(
      deviceToken = token,
      touchpointPlatform =
        when (appVariant) {
          Emergency -> FcmCustomer
          Customer -> FcmCustomer
          Development -> FcmTeam
          Alpha -> FcmTeam // android doesn't use this config
          Team -> FcmTeam
        }
    )
  }

  private suspend fun getToken(): String? {
    return withContext(Dispatchers.IO) {
      suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance()
          .token
          .addOnSuccessListener { token ->
            continuation.resume(token)
          }
          .addOnFailureListener { exception ->
            logWarn(throwable = exception) { "Failed to get FCM token" }
            continuation.resume(null)
          }
          .addOnCanceledListener {
            continuation.cancel()
          }
      }
    }
  }
}
