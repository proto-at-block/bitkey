package build.wallet.f8e.client

/**
 * Thrown when an F8e operation is attempted in offline mode.
 */
object OfflineOperationException : UnsupportedOperationException("App is running in Offline-only mode.")
