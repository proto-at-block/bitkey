package build.wallet.platform.permissions

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class PermissionCheckerImpl : PermissionChecker {
  override fun getPermissionStatus(permission: Permission): PermissionStatus {
    return PermissionStatus.NotDetermined
  }
}
