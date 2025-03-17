package build.wallet.cloud.store

/**
 * Each platform should implement its own set of supported cloud storage service providers.
 *
 * Example:
 * - Android supports Google Drive.
 * - iOS supports iCloud (and could support Google Drive in the future).
 */
interface CloudStoreServiceProvider {
  val name: String
}

expect fun cloudServiceProvider(): CloudStoreServiceProvider
