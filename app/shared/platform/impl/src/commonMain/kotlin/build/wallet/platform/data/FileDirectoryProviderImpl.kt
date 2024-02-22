package build.wallet.platform.data

import build.wallet.platform.PlatformContext

expect class FileDirectoryProviderImpl constructor(
  platformContext: PlatformContext,
) : FileDirectoryProvider
