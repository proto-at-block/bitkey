package build.wallet.platform.permissions

import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PermissionStatus.Denied

class PermissionCheckerMock(
  var permissionsOn: Boolean = false,
) : PermissionChecker {
  override fun getPermissionStatus(permission: Permission): PermissionStatus {
    return if (permissionsOn) {
      Authorized
    } else {
      Denied
    }
  }
}
