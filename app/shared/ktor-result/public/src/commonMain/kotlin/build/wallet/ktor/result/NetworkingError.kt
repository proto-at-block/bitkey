package build.wallet.ktor.result

/**
 * Describes a networking error. Used to allow mapping [HttpBodyError] and [HttpError] error
 * results together.
 */
sealed class NetworkingError : Error()
