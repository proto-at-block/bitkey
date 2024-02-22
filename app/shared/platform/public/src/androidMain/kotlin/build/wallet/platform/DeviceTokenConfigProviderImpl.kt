package build.wallet.platform

import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Beta
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team
import build.wallet.platform.config.DeviceTokenConfig
import build.wallet.platform.config.DeviceTokenConfigProvider
import build.wallet.platform.config.TouchpointPlatform.Fcm
import build.wallet.platform.config.TouchpointPlatform.FcmCustomer
import build.wallet.platform.config.TouchpointPlatform.FcmTeam
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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
    return suspendCoroutine { continuation ->
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
