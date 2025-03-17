package build.wallet.platform.permissions

enum class PermissionStatus {
  // Permissions are not determined by the system
  NotDetermined,

  // Permissions are not allowed
  Denied,

  // Permissions are allowed
  Authorized,
}
