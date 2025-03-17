package build.wallet.notifications

import bitkey.auth.AuthTokenScope
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.platform.config.TouchpointPlatform

interface DeviceTokenManager {
  /**
   * Attempts to add the given device token to the server for the active account
   * or account being onboarded, if any.
   *
   * Platforms should call this when they receive a device token.
   */
  suspend fun addDeviceTokenIfActiveOrOnboardingAccount(
    deviceToken: String,
    touchpointPlatform: TouchpointPlatform,
  ): DeviceTokenManagerResult<Unit, DeviceTokenManagerError>

  /**
   * Attempts to add the stored device token, if any, for the account to the server.
   *
   * Platforms will call the above [addDeviceTokenIfActiveOrOnboardingAccount] when they receive
   * a device token, but they will also store it so it is returned by [DeviceTokenConfigProvider]
   * which can then be later used by this method to try to add the token to the server.
   */
  suspend fun addDeviceTokenIfPresentForAccount(
    fullAccountId: FullAccountId,
    authTokenScope: AuthTokenScope,
  ): DeviceTokenManagerResult<Unit, DeviceTokenManagerError>
}

sealed class DeviceTokenManagerError : Error() {
  data object NoDeviceToken : DeviceTokenManagerError() {
    override val cause: Throwable? = null
  }

  data object NoKeybox : DeviceTokenManagerError() {
    override val cause: Throwable? = null
  }

  data class NetworkingError(
    override val cause: Throwable,
  ) : DeviceTokenManagerError()
}
