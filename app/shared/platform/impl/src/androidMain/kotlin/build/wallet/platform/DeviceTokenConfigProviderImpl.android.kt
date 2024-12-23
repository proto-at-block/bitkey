package build.wallet.platform

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.*
import build.wallet.platform.config.DeviceTokenConfig
import build.wallet.platform.config.DeviceTokenConfigProvider
import build.wallet.platform.config.TouchpointPlatform.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@BitkeyInject(AppScope::class)
class DeviceTokenConfigProviderImpl(
  private val appVariant: AppVariant,
) : DeviceTokenConfigProvider {
  override suspend fun config(): DeviceTokenConfig? {
    return DeviceTokenConfig(
      deviceToken = getToken(),
      touchpointPlatform =
        when (appVariant) {
          Emergency -> FcmCustomer
          Customer -> FcmCustomer
          Beta -> Fcm
          Development -> FcmTeam
          Team -> FcmTeam
        }
    )
  }

  private suspend fun getToken(): String {
    return withContext(Dispatchers.IO) {
      suspendCoroutine { continuation ->
        FirebaseMessaging.getInstance()
          .token
          .addOnCompleteListener { task ->
            if (!task.isSuccessful) {
              continuation.resumeWithException(task.exception ?: IllegalStateException(task.result))
            } else {
              continuation.resume(task.result)
            }
          }
      }
    }
  }
}
