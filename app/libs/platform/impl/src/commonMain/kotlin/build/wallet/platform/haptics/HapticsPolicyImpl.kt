package build.wallet.platform.haptics

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.permissions.Permission.HapticsVibrator
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.platform.permissions.PermissionStatus.Authorized

@BitkeyInject(AppScope::class)
class HapticsPolicyImpl(
  private val permissionChecker: PermissionChecker,
) : HapticsPolicy {
  override suspend fun canVibrate(): Boolean {
    return permissionChecker.getPermissionStatus(HapticsVibrator) == Authorized
  }
}
