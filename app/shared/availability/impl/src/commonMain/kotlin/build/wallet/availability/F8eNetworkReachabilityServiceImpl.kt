package build.wallet.availability

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.client.OfflineOperationException
import build.wallet.f8e.client.plugins.disableReachabilityCheck
import build.wallet.f8e.client.plugins.withEnvironment
import build.wallet.f8e.url
import build.wallet.ktor.result.HttpError
import build.wallet.ktor.result.catching
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform.Android
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import io.ktor.client.*
import io.ktor.client.request.*

@BitkeyInject(AppScope::class)
class F8eNetworkReachabilityServiceImpl(
  private val deviceInfoProvider: DeviceInfoProvider,
) : F8eNetworkReachabilityService {
  override suspend fun checkConnection(
    httpClient: HttpClient,
    f8eEnvironment: F8eEnvironment,
  ): Result<Unit, HttpError> {
    val deviceInfo = deviceInfoProvider.getDeviceInfo()
    val isAndroidEmulator = deviceInfo.devicePlatform == Android && deviceInfo.isEmulator
    return when (f8eEnvironment) {
      F8eEnvironment.ForceOffline -> Err(HttpError.NetworkError(OfflineOperationException))
      else -> httpClient.catching {
        head(f8eEnvironment.url(isAndroidEmulator)) {
          disableReachabilityCheck()
          withEnvironment(f8eEnvironment)
        }
      }.map {}
    }
  }
}
