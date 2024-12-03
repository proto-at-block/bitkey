package build.wallet.platform.data

data class FileManagerError(
  override val cause: Throwable,
) : Error()
