package build.wallet.notifications

import bitkey.account.AccountConfigService
import bitkey.auth.AuthTokenScope
import build.wallet.account.AccountService
import build.wallet.account.getAccountOrNull
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.onboarding.AddDeviceTokenF8eClient
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logDebug
import build.wallet.logging.logNetworkFailure
import build.wallet.notifications.DeviceTokenManagerError.NetworkingError
import build.wallet.notifications.DeviceTokenManagerError.NoDeviceToken
import build.wallet.notifications.DeviceTokenManagerResult.Err
import build.wallet.platform.config.DeviceTokenConfigProvider
import build.wallet.platform.config.TouchpointPlatform
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform
import com.github.michaelbull.result.get
import com.github.michaelbull.result.mapError

@BitkeyInject(AppScope::class)
class DeviceTokenManagerImpl(
  private val addDeviceTokenF8eClient: AddDeviceTokenF8eClient,
  private val deviceTokenConfigProvider: DeviceTokenConfigProvider,
  private val keyboxDao: KeyboxDao,
  private val accountConfigService: AccountConfigService,
  private val accountService: AccountService,
  private val deviceInfoProvider: DeviceInfoProvider,
) : DeviceTokenManager, DeviceTokenAppWorker {
  override suspend fun executeWork() {
    // Only execute work on Android devices, since iOS device tokens work differently
    // On iOS, we register for notifications when the permission is granted or when it changes
    // from a change in settings. This is handled in NotificationManagerImpl.swift
    if (deviceInfoProvider.getDeviceInfo().devicePlatform != DevicePlatform.Android) {
      return
    }
    val account = accountService.getAccountOrNull<FullAccount>()

    account.get()?.let { account ->
      addDeviceTokenIfPresentForAccount(account.accountId, AuthTokenScope.Global)
    }
  }

  override suspend fun addDeviceTokenIfActiveOrOnboardingAccount(
    deviceToken: String,
    touchpointPlatform: TouchpointPlatform,
  ): DeviceTokenManagerResult<Unit, DeviceTokenManagerError> {
    // Try active keybox first, then onboarding keybox, then return an error
    val keybox =
      keyboxDao.getActiveOrOnboardingKeybox().get()
        ?: return Err(DeviceTokenManagerError.NoKeybox)

    logDebug { "Attempting to add device token for active or onboarding keybox" }

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
    authTokenScope: AuthTokenScope,
  ): DeviceTokenManagerResult<Unit, DeviceTokenManagerError> {
    val deviceTokenConfig = deviceTokenConfigProvider.config() ?: return Err(NoDeviceToken)

    val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
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
