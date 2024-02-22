package build.wallet.platform.data

data class FileManagerError(
  val throwable: Throwable,
) : Error()
