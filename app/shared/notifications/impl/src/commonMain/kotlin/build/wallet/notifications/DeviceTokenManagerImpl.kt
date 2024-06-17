package build.wallet.notifications

import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.onboarding.AddDeviceTokenF8eClient
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.log
import build.wallet.logging.logNetworkFailure
import build.wallet.notifications.DeviceTokenManagerError.NetworkingError
import build.wallet.notifications.DeviceTokenManagerError.NoDeviceToken
import build.wallet.notifications.DeviceTokenManagerResult.Err
import build.wallet.platform.config.DeviceTokenConfigProvider
import build.wallet.platform.config.TouchpointPlatform
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapError

class DeviceTokenManagerImpl(
  private val addDeviceTokenF8eClient: AddDeviceTokenF8eClient,
  private val deviceTokenConfigProvider: DeviceTokenConfigProvider,
  private val keyboxDao: KeyboxDao,
) : DeviceTokenManager {
  override suspend fun addDeviceTokenIfActiveOrOnboardingAccount(
    deviceToken: String,
    touchpointPlatform: TouchpointPlatform,
  ): DeviceTokenManagerResult<Unit, DeviceTokenManagerError> {
    // Try active keybox first, then onboarding keybox, then return an error
    val keybox =
      keyboxDao.getActiveOrOnboardingKeybox().get()
        ?: return Err(DeviceTokenManagerError.NoKeybox)

    log { "Attempting to add device token for active or onboarding keybox" }

    return addDeviceTokenF8eClient.add(
      f8eEnvironment = keybox.config.f8eEnvironment,
      fullAccountId = keybox.fullAccountId,
      token = deviceToken,
      touchpointPlatform = touchpointPlatform,
      authTokenScope = AuthTokenScope.Global
    )
      .logNetworkFailure { "Failed to add device token" }
      .mapError { NetworkingError(it) }
      .toDeviceTokenManagerResult()
  }

  override suspend fun addDeviceTokenIfPresentForAccount(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    authTokenScope: AuthTokenScope,
  ): DeviceTokenManagerResult<Unit, DeviceTokenManagerError> {
    val deviceTokenConfig =
      deviceTokenConfigProvider.config()
        ?: return Err(NoDeviceToken)

    log { "Attempting to add device token for account $fullAccountId" }

    return addDeviceTokenF8eClient.add(
      f8eEnvironment = f8eEnvironment,
      fullAccountId = fullAccountId,
      token = deviceTokenConfig.deviceToken,
      touchpointPlatform = deviceTokenConfig.touchpointPlatform,
      authTokenScope = authTokenScope
    )
      .logNetworkFailure { "Failed to add device token" }
      .mapError { NetworkingError(it) }
      .toDeviceTokenManagerResult()
  }
}
