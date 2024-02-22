package build.wallet.notifications

import app.cash.turbine.Turbine
import build.wallet.auth.AuthTokenScope
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.config.TouchpointPlatform

class DeviceTokenManagerMock(
  turbine: (String) -> Turbine<Any>,
) : DeviceTokenManager {
  val addDeviceTokenIfActiveOrOnboardingAccountCalls =
    turbine("addDeviceTokenIfActiveAccount calls")
  val addDeviceTokenIfPresentForAccountCalls = turbine("addDeviceTokenIfPresentForAccount calls")
  var addDeviceTokenIfPresentForAccountReturn:
    DeviceTokenManagerResult<Unit, DeviceTokenManagerError> = DeviceTokenManagerResult.Ok(Unit)

  override suspend fun addDeviceTokenIfActiveOrOnboardingAccount(
    deviceToken: String,
    touchpointPlatform: TouchpointPlatform,
  ): DeviceTokenManagerResult<Unit, DeviceTokenManagerError> {
    addDeviceTokenIfActiveOrOnboardingAccountCalls.add(Unit)
    return DeviceTokenManagerResult.Ok(Unit)
  }

  override suspend fun addDeviceTokenIfPresentForAccount(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    authTokenScope: AuthTokenScope,
  ): DeviceTokenManagerResult<Unit, DeviceTokenManagerError> {
    addDeviceTokenIfPresentForAccountCalls.add(Unit)
    return addDeviceTokenIfPresentForAccountReturn
  }

  fun reset() {
    addDeviceTokenIfPresentForAccountReturn = DeviceTokenManagerResult.Ok(Unit)
  }
}
