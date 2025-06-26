package build.wallet.cloud.backup.csek

/**
 * Generates a [Sek] (Storage Encryption Key).
 *
 * These keys are then used to encrypt data to be stored in the cloud, as well as F8e.
 */
interface SekGenerator {
  suspend fun generate(): Sek
}
