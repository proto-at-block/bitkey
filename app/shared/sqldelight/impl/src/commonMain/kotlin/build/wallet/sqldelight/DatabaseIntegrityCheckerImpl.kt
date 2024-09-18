package build.wallet.sqldelight

import build.wallet.platform.data.FileDirectoryProvider

expect class DatabaseIntegrityCheckerImpl(
  fileDirectoryProvider: FileDirectoryProvider,
) : DatabaseIntegrityChecker
