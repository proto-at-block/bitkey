package build.wallet.platform.permissions

import build.wallet.platform.PlatformContext

expect class PushNotificationPermissionStatusProviderImpl constructor(
  platformContext: PlatformContext,
) : PushNotificationPermissionStatusProvider
