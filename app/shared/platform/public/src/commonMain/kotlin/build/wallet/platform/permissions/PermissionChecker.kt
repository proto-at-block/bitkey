package build.wallet.platform.permissions

interface PermissionChecker {
  fun getPermissionStatus(permission: Permission): PermissionStatus
}
