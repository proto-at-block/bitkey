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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
          Development -> FcmTeam
          Alpha -> FcmTeam // android doesn't use this config
          Team -> FcmTeam
        }
    )
  }

  private suspend fun getToken(): String {
    return withContext(Dispatchers.IO) {
      suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance()
          .token
          .addOnSuccessListener { token ->
            continuation.resume(token)
          }
          .addOnFailureListener {
            continuation.resumeWithException(it)
          }
          .addOnCanceledListener {
            continuation.cancel()
          }
      }
    }
  }
}
